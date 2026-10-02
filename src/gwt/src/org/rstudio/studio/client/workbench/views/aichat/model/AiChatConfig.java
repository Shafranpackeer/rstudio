/*
 * AiChatConfig.java
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

import com.google.gwt.core.client.JavaScriptObject;

public class AiChatConfig extends JavaScriptObject
{
   public static final String PROVIDER_ANTHROPIC = "anthropic"; //$NON-NLS-1$
   public static final String PROVIDER_OPENAI = "openai"; //$NON-NLS-1$
   public static final String PROVIDER_CUSTOM = "custom"; //$NON-NLS-1$

   protected AiChatConfig()
   {
   }

   public final native String getProvider() /*-{ return this.provider || "anthropic"; }-*/;
   public final native String getModel() /*-{ return this.model || ""; }-*/;
   public final native String getBaseUrl() /*-{ return this.base_url || ""; }-*/;
   public final native boolean hasApiKey() /*-{ return !!this.has_api_key; }-*/;

   /** One of "saved", "environment", or "none". */
   public final native String getApiKeySource() /*-{ return this.api_key_source || "none"; }-*/;
   public final native String getApiKeyEnvVar() /*-{ return this.api_key_env_var || ""; }-*/;

   /**
    * True when a key is saved for the given key slot: the provider name for
    * Anthropic and OpenAI, or "custom|<base url>" for custom endpoints (see
    * keySlot() in SessionAiChat.cpp).
    */
   public final native boolean hasSavedKey(String slot) /*-{
      var slots = this.saved_key_slots || [];
      for (var i = 0; i < slots.length; i++)
         if (slots[i] === slot)
            return true;
      return false;
   }-*/;
}
