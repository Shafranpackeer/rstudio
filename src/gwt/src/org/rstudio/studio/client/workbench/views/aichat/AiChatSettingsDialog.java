/*
 * AiChatSettingsDialog.java
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
package org.rstudio.studio.client.workbench.views.aichat;

import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.widget.FormLabel;
import org.rstudio.core.client.widget.ModalDialog;
import org.rstudio.core.client.widget.OperationWithInput;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.workbench.views.aichat.model.AiChatConfig;

import com.google.gwt.aria.client.Roles;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

public class AiChatSettingsDialog extends ModalDialog<AiChatSettingsDialog.Result>
{
   public static class Result
   {
      public Result(String provider, String model, String baseUrl, String apiKey)
      {
         this.provider = provider;
         this.model = model;
         this.baseUrl = baseUrl;
         this.apiKey = apiKey;
      }

      public final String provider;
      public final String model;
      public final String baseUrl;

      /** null keeps the saved key, "" removes it, anything else replaces it. */
      public final String apiKey;
   }

   public AiChatSettingsDialog(AiChatConfig config,
                               OperationWithInput<Result> onSaved)
   {
      super(constants_.settingsCaption(), Roles.getDialogRole(), onSaved);
      setThemeAware(true);

      config_ = config;

      provider_ = new ListBox();
      provider_.addItem(constants_.providerAnthropic(), AiChatConfig.PROVIDER_ANTHROPIC);
      provider_.addItem(constants_.providerOpenAi(), AiChatConfig.PROVIDER_OPENAI);
      provider_.addItem(constants_.providerCustom(), AiChatConfig.PROVIDER_CUSTOM);
      for (int i = 0; i < provider_.getItemCount(); i++)
      {
         if (StringUtil.equals(provider_.getValue(i), config.getProvider()))
            provider_.setSelectedIndex(i);
      }
      provider_.addChangeHandler(event -> onProviderChanged());

      model_ = new TextBox();
      model_.setWidth("300px");

      baseUrl_ = new TextBox();
      baseUrl_.setWidth("300px");

      apiKey_ = new PasswordTextBox();
      apiKey_.setWidth("300px");
      apiKey_.getElement().setAttribute("autocomplete", "off");

      keyStatus_ = new Label();
      keyStatus_.getElement().getStyle().setFontSize(11, Unit.PX);
      keyStatus_.getElement().getStyle().setOpacity(0.8);

      removeKey_ = new CheckBox(constants_.removeApiKey());

      customHelp_ = new Label(constants_.customHelp());
      customHelp_.getElement().getStyle().setProperty("maxWidth", "380px");
      customHelp_.getElement().getStyle().setMarginTop(8, Unit.PX);
      customHelp_.getElement().getStyle().setFontSize(11, Unit.PX);

      // show the configured values for the configured provider; the defaults
      // for other providers appear as placeholders when switching
      model_.setText(config.getModel());
      baseUrl_.setText(config.getBaseUrl());
      updateProviderDependentState();
   }

   @Override
   protected Widget createMainWidget()
   {
      VerticalPanel panel = new VerticalPanel();
      panel.setSpacing(2);

      panel.add(new FormLabel(constants_.providerLabel(), provider_));
      panel.add(provider_);
      panel.add(spacer());

      panel.add(new FormLabel(constants_.modelLabel(), model_));
      panel.add(model_);
      panel.add(spacer());

      panel.add(new FormLabel(constants_.baseUrlLabel(), baseUrl_));
      panel.add(baseUrl_);
      panel.add(spacer());

      panel.add(new FormLabel(constants_.apiKeyLabel(), apiKey_));
      panel.add(apiKey_);
      panel.add(keyStatus_);
      panel.add(removeKey_);

      panel.add(customHelp_);

      Label storageHelp = new Label(constants_.keyStorageHelp());
      storageHelp.getElement().getStyle().setProperty("maxWidth", "380px");
      storageHelp.getElement().getStyle().setMarginTop(8, Unit.PX);
      storageHelp.getElement().getStyle().setFontSize(11, Unit.PX);
      panel.add(storageHelp);

      return panel;
   }

   @Override
   protected Result collectInput()
   {
      String apiKey = apiKey_.getText().trim();
      String keyValue;
      if (!apiKey.isEmpty())
         keyValue = apiKey;
      else if (removeKey_.isVisible() && removeKey_.getValue())
         keyValue = "";
      else
         keyValue = null;

      return new Result(selectedProvider(),
                        model_.getText().trim(),
                        baseUrl_.getText().trim(),
                        keyValue);
   }

   @Override
   protected boolean validate(Result result)
   {
      if (StringUtil.equals(result.provider, AiChatConfig.PROVIDER_CUSTOM) &&
          StringUtil.isNullOrEmpty(result.model))
      {
         RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
               constants_.settingsCaption(), constants_.modelRequired());
         return false;
      }
      return true;
   }

   private Widget spacer()
   {
      Label spacer = new Label();
      spacer.setHeight("6px");
      return spacer;
   }

   private String selectedProvider()
   {
      return provider_.getSelectedValue();
   }

   private void onProviderChanged()
   {
      // values entered for one provider rarely make sense for another
      boolean isConfigured = StringUtil.equals(selectedProvider(), config_.getProvider());
      model_.setText(isConfigured ? config_.getModel() : "");
      baseUrl_.setText(isConfigured ? config_.getBaseUrl() : "");
      updateProviderDependentState();
   }

   private void updateProviderDependentState()
   {
      String provider = selectedProvider();
      boolean isCustom = StringUtil.equals(provider, AiChatConfig.PROVIDER_CUSTOM);
      boolean isConfigured = StringUtil.equals(provider, config_.getProvider());

      model_.getElement().setAttribute("placeholder", defaultModel(provider));
      baseUrl_.getElement().setAttribute("placeholder", defaultBaseUrl(provider));
      customHelp_.setVisible(isCustom);

      String keySource = isConfigured ? config_.getApiKeySource() : "none";
      if (StringUtil.equals(keySource, "saved"))
         keyStatus_.setText(constants_.apiKeySaved());
      else if (StringUtil.equals(keySource, "environment"))
         keyStatus_.setText(constants_.apiKeyFromEnvironment(envVar(provider)));
      else
         keyStatus_.setText(constants_.apiKeyMissing(envVar(provider)));

      removeKey_.setValue(false);
      removeKey_.setVisible(StringUtil.equals(keySource, "saved"));
   }

   // These mirror the defaults in SessionAiChat.cpp, and are only used as
   // placeholders; the session applies the real defaults.
   private static String defaultModel(String provider)
   {
      if (StringUtil.equals(provider, AiChatConfig.PROVIDER_OPENAI))
         return "gpt-5"; //$NON-NLS-1$
      if (StringUtil.equals(provider, AiChatConfig.PROVIDER_CUSTOM))
         return "llama3.1"; //$NON-NLS-1$
      return "claude-sonnet-5-5"; //$NON-NLS-1$
   }

   private static String defaultBaseUrl(String provider)
   {
      if (StringUtil.equals(provider, AiChatConfig.PROVIDER_OPENAI))
         return "https://api.openai.com/v1"; //$NON-NLS-1$
      if (StringUtil.equals(provider, AiChatConfig.PROVIDER_CUSTOM))
         return "http://localhost:11434/v1"; //$NON-NLS-1$
      return "https://api.anthropic.com"; //$NON-NLS-1$
   }

   private static String envVar(String provider)
   {
      if (StringUtil.equals(provider, AiChatConfig.PROVIDER_OPENAI))
         return "OPENAI_API_KEY"; //$NON-NLS-1$
      if (StringUtil.equals(provider, AiChatConfig.PROVIDER_CUSTOM))
         return "RSTUDIO_AI_API_KEY"; //$NON-NLS-1$
      return "ANTHROPIC_API_KEY"; //$NON-NLS-1$
   }

   private final AiChatConfig config_;
   private final ListBox provider_;
   private final TextBox model_;
   private final TextBox baseUrl_;
   private final PasswordTextBox apiKey_;
   private final Label keyStatus_;
   private final CheckBox removeKey_;
   private final Label customHelp_;

   private static final AiChatConstants constants_ = GWT.create(AiChatConstants.class);
}
