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
package com.okta.directauth.cli.view;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.okta.directauth.cli.model.CrossAppAccessConfig;
import com.okta.directauth.cli.model.OAuth2Flow;
import com.okta.directauth.cli.model.OAuth2Screen;
import com.okta.directauth.cli.viewmodel.CrossAppAccessViewModel;
import com.okta.directauth.cli.viewmodel.OAuth2ViewModel;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies {@link OAuth2ConsoleView}'s Cross App Access latch-await contract — specifically that
 * {@link OAuth2ConsoleView#stop()} racing an in-flight action is distinguished from that action
 * actually failing (see {@code awaitCrossAppAccessAction}'s {@code ActionOutcome}).
 */
public class OAuth2ConsoleViewTest {
  private OAuth2ViewModel viewModel;
  private CrossAppAccessViewModel crossAppAccessViewModel;
  private ConsoleInput input;
  private ConsoleOutput output;
  private OAuth2ConsoleView view;

  @Before
  public void setUp() {
    viewModel = mock(OAuth2ViewModel.class);
    crossAppAccessViewModel = mock(CrossAppAccessViewModel.class);
    input = mock(ConsoleInput.class);
    output = mock(ConsoleOutput.class);
    view = new OAuth2ConsoleView(viewModel, crossAppAccessViewModel, input, output);

    when(viewModel.getCurrentScreen()).thenReturn(OAuth2Screen.MENU);
    when(crossAppAccessViewModel.validateConfig())
        .thenReturn(new CrossAppAccessConfig.Validation.Complete());
    when(crossAppAccessViewModel.getConfig())
        .thenReturn(
            new CrossAppAccessConfig.Builder()
                .idpIssuer("https://idp.example.com")
                .idpClientId("idp-client-id")
                .authorizationServerId("ausOther")
                .clientId("target-client-id")
                .build());
    when(crossAppAccessViewModel.hasSession()).thenReturn(false);
  }

  @Test
  public void stopDuringCrossAppAccessSignIn_DoesNotShowErrorOrPromptFurther() {
    // Selects "Cross App Access" (the last menu entry); nothing further is ever read, since
    // stop() must end the run() loop right after signIn() returns.
    when(input.readLine()).thenReturn(Integer.toString(OAuth2Flow.values().length));
    // Simulates stop() racing signIn()'s latch.await() (e.g. a shutdown hook firing while the
    // browser redirect is still pending): the mocked signIn() call itself triggers stop() before
    // returning, exactly as a concurrent shutdown would release the latch out from under an
    // in-flight action.
    doAnswer(
            invocation -> {
              view.stop();
              return null;
            })
        .when(crossAppAccessViewModel)
        .signIn();

    view.run();

    verify(output, never()).println(contains("Cross App Access Error"));
    verify(output, never()).println(contains("=== Subject ==="));
    verify(crossAppAccessViewModel, never()).selectSubjectKind(any());
    verify(crossAppAccessViewModel, never()).reset();
  }

  @Test
  public void crossAppAccessSignIn_Error_ShowsErrorScreen() {
    // Contrast case: signIn() actually failing (not a shutdown) must still show the error screen
    // and return to the menu, then exit on "0".
    when(input.readLine())
        .thenReturn(Integer.toString(OAuth2Flow.values().length), "continuing", "0");
    // Simulates the real ViewModel's own behavior on failure: notifies the CROSS_APP_ACCESS_ERROR
    // screen change (releasing the latch, as the production onScreenChanged listener callback
    // would) and reflects it in getCurrentScreen().
    doAnswer(
            invocation -> {
              when(crossAppAccessViewModel.getCurrentScreen())
                  .thenReturn(OAuth2Screen.CROSS_APP_ACCESS_ERROR);
              view.onScreenChanged(OAuth2Screen.CROSS_APP_ACCESS_ERROR);
              return null;
            })
        .when(crossAppAccessViewModel)
        .signIn();

    view.run();

    verify(output).println(contains("Cross App Access Error"));
    verify(crossAppAccessViewModel).reset();
  }
}
