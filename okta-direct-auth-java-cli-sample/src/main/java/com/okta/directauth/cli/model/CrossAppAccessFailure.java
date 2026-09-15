/*
 * Copyright 2022-Present Okta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.okta.directauth.cli.model;

import com.okta.oauth2.kmp.CrossAppAccessException;
import java.util.HashSet;
import java.util.Set;

/**
 * Attributes a Cross App Access failure to the server that produced it, mirroring the shared
 * module's Kotlin classifier exactly so both samples describe identical failures.
 *
 * <p>{@link CrossAppAccessException} exists precisely to name which server rejected an exchange,
 * with the original transport failure attached as its cause. A handler that walks straight to the
 * root cause before checking for this type — as this sample's existing OAuth2 error handling does —
 * discards the attribution and reports a generic transport message instead. {@link #classify} scans
 * for it <em>first</em>, and only then walks to the root cause for the server's own error text. It
 * also traverses the full cause chain rather than inspecting only the top-level throwable, since
 * {@link java.util.concurrent.CompletableFuture} wraps failures in a {@link
 * java.util.concurrent.CompletionException}.
 */
public final class CrossAppAccessFailure {
  private final Attribution attribution;
  private final String message;

  private CrossAppAccessFailure(Attribution attribution, String message) {
    this.attribution = attribution;
    this.message = message;
  }

  public Attribution getAttribution() {
    return attribution;
  }

  public String getMessage() {
    return message;
  }

  /**
   * Classifies {@code error}, with no subject kind context.
   *
   * @param error the failure to classify
   * @return the classified failure
   */
  public static CrossAppAccessFailure classify(Throwable error) {
    return classify(error, null);
  }

  /**
   * Classifies {@code error} and produces a developer-facing message.
   *
   * @param error the failure to classify
   * @param subjectKind which subject kind was presented when the failure occurred, or null if
   *     unknown. When a non-default kind was used and an identity-provider denial results, the
   *     message names that kind and suggests the identity token instead. Otherwise, an
   *     identity-provider denial names the administrator-configured trust relationship as the most
   *     common cause, since it is the least discoverable from the server's own error text.
   * @return the classified failure
   */
  public static CrossAppAccessFailure classify(Throwable error, SubjectKind subjectKind) {
    CrossAppAccessException found = findCrossAppAccessException(error);

    if (found == null) {
      return new CrossAppAccessFailure(Attribution.CONFIGURATION, rootMessage(error));
    }

    String serverMessage = rootMessage(found);

    if (found instanceof CrossAppAccessException.IdpExchangeFailed) {
      String hint =
          (subjectKind != null && subjectKind != SubjectKind.IDENTITY)
              ? "The "
                  + subjectKind.name().toLowerCase()
                  + " token subject may not be accepted by this org's trust configuration — try"
                  + " the identity token instead."
              : "This is commonly caused by a missing administrator-configured trust relationship"
                  + " between the requesting app and the resource app.";
      return new CrossAppAccessFailure(
          Attribution.IDENTITY_PROVIDER,
          "The identity provider rejected the request: " + serverMessage + ". " + hint);
    }

    // The only other CrossAppAccessException subclass; found here rather than in an else-branch
    // instanceof check so a future third subclass fails loudly instead of silently misattributing.
    if (found instanceof CrossAppAccessException.TargetRedemptionFailed) {
      return new CrossAppAccessFailure(
          Attribution.RESOURCE_SERVER,
          "The resource authorization server rejected the request: " + serverMessage);
    }

    throw new IllegalStateException(
        "Unhandled CrossAppAccessException subclass: " + found.getClass());
  }

  /**
   * Scans the cause chain for a {@link CrossAppAccessException}, before any root-cause unwrapping.
   */
  private static CrossAppAccessException findCrossAppAccessException(Throwable error) {
    Set<Throwable> seen = new HashSet<>();
    Throwable current = error;
    while (current != null && seen.add(current)) {
      if (current instanceof CrossAppAccessException) {
        return (CrossAppAccessException) current;
      }
      current = current.getCause();
    }
    return null;
  }

  /** Walks to the root cause and returns its message, for the server's own error text. */
  private static String rootMessage(Throwable error) {
    Set<Throwable> seen = new HashSet<>();
    Throwable current = error;
    while (true) {
      Throwable next = current.getCause();
      if (next == null || !seen.add(current)) {
        break;
      }
      current = next;
    }
    String message = current.getMessage();
    return message != null ? message : current.getClass().getSimpleName();
  }

  /** Which authorization server — if any — is responsible for a failed Cross App Access attempt. */
  public enum Attribution {
    /** The IdP authorization server denied the request. */
    IDENTITY_PROVIDER,
    /** The resource authorization server denied the request. */
    RESOURCE_SERVER,
    /** The failure occurred before any network request — a local configuration problem. */
    CONFIGURATION
  }
}
