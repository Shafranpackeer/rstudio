/*
 * AiChatHttpResult.java
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

public class AiChatHttpResult extends JavaScriptObject
{
   protected AiChatHttpResult()
   {
   }

   /** The HTTP status code, or 0 when the request never got a response. */
   public final native int getStatus() /*-{ return this.status || 0; }-*/;
   public final native String getBody() /*-{ return this.body || ""; }-*/;

   /** A transport-level error message; empty when a response was received. */
   public final native String getError() /*-{ return this.error || ""; }-*/;
}
