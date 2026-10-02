/*
 * AiChatServerOperations.java
 *
 * Copyright (C) 2026 by Posit Software, PBC
 *
 * Unless you have received this program directly from Posit Software pursuant
 * to the terms of a commercial license agreement with Posit Software, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */
package org.rstudio.studio.client.workbench.views.aichat.model;

import org.rstudio.studio.client.server.ServerRequestCallback;

import com.google.gwt.core.client.JavaScriptObject;

public interface AiChatServerOperations
{
   void aiChatGetConfig(ServerRequestCallback<AiChatConfig> requestCallback);

   /**
    * Saves the provider settings. Pass null for apiKey (or jevApiKey) to keep
    * the saved key, or an empty string to remove it.
    */
   void aiChatSetConfig(String provider,
                        String model,
                        String baseUrl,
                        String apiKey,
                        boolean jevEnabled,
                        String jevApiKey,
                        ServerRequestCallback<AiChatConfig> requestCallback);

   /**
    * Sends a provider-specific request body to the configured model. The
    * session supplies the model name and credentials.
    */
   void aiChatSendRequest(String body,
                          ServerRequestCallback<AiChatHttpResult> requestCallback);

   /**
    * Sends a Jev (TypeSafe AI) System One request body; the session adds the
    * model and TypeSafe key.
    */
   void aiChatJevRequest(String body,
                         ServerRequestCallback<AiChatHttpResult> requestCallback);

   void aiChatExecuteTool(String name,
                          JavaScriptObject input,
                          ServerRequestCallback<String> requestCallback);
}
