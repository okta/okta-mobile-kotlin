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
package com.okta.authfoundation.api.log

/**
 * Represents the severity level of a log message.
 *
 * This is used to control the verbosity of logging output and to categorize messages
 * based on their importance.
 */
enum class LogLevel {
    /**
     * Designates fine-grained informational events that are most useful to debug an application.
     */
    DEBUG,

    /**
     * Designates informational messages that highlight the progress of the application at a
     * coarse-grained level.
     */
    INFO,

    /**
     * Designates potentially harmful situations or warnings that do not prevent the
     * application from continuing.
     */
    WARN,

    /**
     * Designates error events that might still allow the application to continue running.
     */
    ERROR,
}
