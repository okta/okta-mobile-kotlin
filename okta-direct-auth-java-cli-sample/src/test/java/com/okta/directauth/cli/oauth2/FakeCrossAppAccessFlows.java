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
package com.okta.directauth.cli.oauth2;

import com.okta.authfoundation.client.TokenInfo;
import com.okta.authfoundation.client.dto.IntrospectInfo;
import com.okta.oauth2.kmp.IdJagAssertion;
import com.okta.oauth2.kmp.SubjectAssertion;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Test double for {@link CrossAppAccessFlows}.
 *
 * <p>Returns pre-configured futures so tests can exercise success, error, and repeat-redemption
 * paths without a network. Records the last invoked method and arguments for assertion, and how
 * many times {@link #start} and {@link #redeem} were each called — the count a repeat-redemption
 * test needs to prove only one {@link #start} occurred for two {@link #redeem}s.
 */
public final class FakeCrossAppAccessFlows implements CrossAppAccessFlows {
  private CompletableFuture<IdJagAssertion> startResult = new CompletableFuture<>();
  private final Deque<CompletableFuture<TokenInfo>> redeemResults = new ArrayDeque<>();
  private CompletableFuture<TokenInfo> exchangeResult = new CompletableFuture<>();
  private CompletableFuture<IntrospectInfo> introspectResult = new CompletableFuture<>();
  private boolean closed = false;

  private int startCount = 0;
  private int redeemCount = 0;
  private SubjectAssertion lastSubject;
  private IdJagAssertion lastRedeemedIdJag;
  private List<String> lastScope;
  private String lastIntrospectedToken;

  /** Configures {@link #start} to succeed with the given ID-JAG. */
  public void startSucceedWith(IdJagAssertion idJag) {
    startResult = CompletableFuture.completedFuture(idJag);
  }

  /** Configures {@link #start} to fail with the given exception. */
  public void startFailWith(Throwable error) {
    startResult = CompletableFuture.failedFuture(error);
  }

  /**
   * Enqueues one success result for {@link #redeem}. Successive calls return in order — one per
   * invocation — so a repeat-redemption test can return two distinct resource tokens.
   */
  public void enqueueRedeemSuccess(TokenInfo tokenInfo) {
    redeemResults.addLast(CompletableFuture.completedFuture(tokenInfo));
  }

  /** Enqueues one failure result for {@link #redeem}. */
  public void enqueueRedeemFailure(Throwable error) {
    redeemResults.addLast(CompletableFuture.failedFuture(error));
  }

  /** Configures {@link #exchange} to succeed with the given token. */
  public void exchangeSucceedWith(TokenInfo tokenInfo) {
    exchangeResult = CompletableFuture.completedFuture(tokenInfo);
  }

  /** Configures {@link #exchange} to fail with the given exception. */
  public void exchangeFailWith(Throwable error) {
    exchangeResult = CompletableFuture.failedFuture(error);
  }

  /** Configures {@link #introspectResourceToken} to succeed with the given result. */
  public void introspectSucceedWith(IntrospectInfo info) {
    introspectResult = CompletableFuture.completedFuture(info);
  }

  /** Configures {@link #introspectResourceToken} to fail with the given exception. */
  public void introspectFailWith(Throwable error) {
    introspectResult = CompletableFuture.failedFuture(error);
  }

  /** Returns how many times {@link #start} was called. */
  public int getStartCount() {
    return startCount;
  }

  /** Returns how many times {@link #redeem} was called. */
  public int getRedeemCount() {
    return redeemCount;
  }

  public SubjectAssertion getLastSubject() {
    return lastSubject;
  }

  public IdJagAssertion getLastRedeemedIdJag() {
    return lastRedeemedIdJag;
  }

  public List<String> getLastScope() {
    return lastScope;
  }

  public String getLastIntrospectedToken() {
    return lastIntrospectedToken;
  }

  public boolean isClosed() {
    return closed;
  }

  @Override
  public CompletableFuture<IdJagAssertion> start(SubjectAssertion subject, List<String> scope) {
    startCount++;
    lastSubject = subject;
    lastScope = scope;
    return startResult;
  }

  @Override
  public CompletableFuture<TokenInfo> redeem(IdJagAssertion idJag) {
    redeemCount++;
    lastRedeemedIdJag = idJag;
    CompletableFuture<TokenInfo> next = redeemResults.pollFirst();
    return next != null ? next : new CompletableFuture<>();
  }

  @Override
  public CompletableFuture<TokenInfo> exchange(SubjectAssertion subject, List<String> scope) {
    lastSubject = subject;
    lastScope = scope;
    return exchangeResult;
  }

  @Override
  public CompletableFuture<IntrospectInfo> introspectResourceToken(String token) {
    lastIntrospectedToken = token;
    return introspectResult;
  }

  @Override
  public void close() {
    closed = true;
  }
}
