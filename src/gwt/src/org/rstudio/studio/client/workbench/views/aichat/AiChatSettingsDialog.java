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

      service_ = new ListBox();
      for (Preset preset : PRESETS)
         service_.addItem(preset.label, preset.baseUrl);
      service_.addItem(constants_.serviceOther(), "");
      service_.addChangeHandler(event -> onServiceChanged());
      serviceLabel_ = new FormLabel(constants_.serviceLabel(), service_);

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

      apiKeyLabel_ = new FormLabel(constants_.apiKeyLabel(), apiKey_);

      baseUrl_.addChangeHandler(event -> updateKeyStatus());

      toolsHelp_ = new Label(constants_.toolCallingHelp());
      toolsHelp_.getElement().getStyle().setProperty("maxWidth", "380px");
      toolsHelp_.getElement().getStyle().setMarginTop(8, Unit.PX);
      toolsHelp_.getElement().getStyle().setFontSize(11, Unit.PX);
      toolsHelp_.getElement().getStyle().setFontWeight(com.google.gwt.dom.client.Style.FontWeight.BOLD);

      customHelp_ = new Label(constants_.customHelp());
      customHelp_.getElement().getStyle().setProperty("maxWidth", "380px");
      customHelp_.getElement().getStyle().setMarginTop(8, Unit.PX);
      customHelp_.getElement().getStyle().setFontSize(11, Unit.PX);

      // show the configured values for the configured provider; the defaults
      // for other providers appear as placeholders when switching
      model_.setText(config.getModel());
      baseUrl_.setText(config.getBaseUrl());
      selectServiceFor(config.getBaseUrl());
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

      panel.add(serviceLabel_);
      panel.add(service_);
      serviceSpacer_ = spacer();
      panel.add(serviceSpacer_);
      boolean isCustom = StringUtil.equals(selectedProvider(), AiChatConfig.PROVIDER_CUSTOM);
      serviceLabel_.setVisible(isCustom);
      service_.setVisible(isCustom);
      serviceSpacer_.setVisible(isCustom);

      panel.add(new FormLabel(constants_.modelLabel(), model_));
      panel.add(model_);
      panel.add(spacer());

      panel.add(new FormLabel(constants_.baseUrlLabel(), baseUrl_));
      panel.add(baseUrl_);
      panel.add(spacer());

      panel.add(apiKeyLabel_);
      panel.add(apiKey_);
      panel.add(keyStatus_);
      panel.add(removeKey_);

      panel.add(toolsHelp_);
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
      if (StringUtil.equals(selectedProvider(), AiChatConfig.PROVIDER_CUSTOM))
      {
         if (isConfigured)
         {
            selectServiceFor(config_.getBaseUrl());
         }
         else
         {
            service_.setSelectedIndex(0);
            baseUrl_.setText(service_.getSelectedValue());
         }
      }
      updateProviderDependentState();
   }

   private void onServiceChanged()
   {
      String url = service_.getSelectedValue();
      baseUrl_.setText(url);
      model_.setText(StringUtil.equals(normalizeUrl(url), normalizeUrl(config_.getBaseUrl()))
            ? config_.getModel() : "");
      updateProviderDependentState();
      if (url.isEmpty())
         baseUrl_.setFocus(true);
   }

   private void selectServiceFor(String baseUrl)
   {
      String normalized = normalizeUrl(baseUrl);
      for (int i = 0; i < service_.getItemCount(); i++)
      {
         String value = service_.getValue(i);
         if (!value.isEmpty() && StringUtil.equals(normalizeUrl(value), normalized))
         {
            service_.setSelectedIndex(i);
            return;
         }
      }
      service_.setSelectedIndex(service_.getItemCount() - 1);
   }

   private Preset selectedPreset()
   {
      int index = service_.getSelectedIndex();
      return index >= 0 && index < PRESETS.length ? PRESETS[index] : null;
   }

   private void updateProviderDependentState()
   {
      String provider = selectedProvider();
      boolean isCustom = StringUtil.equals(provider, AiChatConfig.PROVIDER_CUSTOM);

      Preset preset = isCustom ? selectedPreset() : null;
      model_.getElement().setAttribute("placeholder",
            preset != null ? preset.modelHint : defaultModel(provider));
      baseUrl_.getElement().setAttribute("placeholder", defaultBaseUrl(provider));

      serviceLabel_.setVisible(isCustom);
      service_.setVisible(isCustom);
      if (serviceSpacer_ != null)
         serviceSpacer_.setVisible(isCustom);
      toolsHelp_.setVisible(isCustom);
      customHelp_.setVisible(isCustom);

      // local servers usually don't need a key; hosted ones do
      boolean keyOptional = isCustom && (preset == null || preset.local);
      apiKeyLabel_.setText(keyOptional ? constants_.apiKeyOptionalLabel() : constants_.apiKeyLabel());

      updateKeyStatus();
   }

   private void updateKeyStatus()
   {
      String provider = selectedProvider();
      String slot = keySlot(provider, baseUrl_.getText());

      boolean saved = config_.hasSavedKey(slot);
      boolean fromEnvironment = !saved &&
            StringUtil.equals(provider, config_.getProvider()) &&
            StringUtil.equals(config_.getApiKeySource(), "environment");

      if (saved)
         keyStatus_.setText(constants_.apiKeySaved());
      else if (fromEnvironment)
         keyStatus_.setText(constants_.apiKeyFromEnvironment(envVar(provider)));
      else
         keyStatus_.setText(constants_.apiKeyMissing(envVar(provider)));

      removeKey_.setValue(false);
      removeKey_.setVisible(saved);
   }

   // Mirrors keySlot() in SessionAiChat.cpp: keys are saved per provider,
   // and per base URL for custom endpoints.
   private static String keySlot(String provider, String baseUrl)
   {
      if (!StringUtil.equals(provider, AiChatConfig.PROVIDER_CUSTOM))
         return provider;

      String url = normalizeUrl(baseUrl);
      if (url.isEmpty())
         url = normalizeUrl(defaultBaseUrl(provider));
      return provider + "|" + url; //$NON-NLS-1$
   }

   private static String normalizeUrl(String url)
   {
      String result = StringUtil.notNull(url).trim();
      while (result.endsWith("/")) //$NON-NLS-1$
         result = result.substring(0, result.length() - 1);
      return result;
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

   // Popular services with OpenAI-compatible endpoints. Model names are only
   // shown as hints, since each service's catalog changes often.
   private static class Preset
   {
      Preset(String label, String baseUrl, String modelHint, boolean local)
      {
         this.label = label;
         this.baseUrl = baseUrl;
         this.modelHint = modelHint;
         this.local = local;
      }

      final String label;
      final String baseUrl;
      final String modelHint;
      final boolean local;
   }

   private static final Preset[] PRESETS = new Preset[] {
      new Preset("Ollama (local)", "http://localhost:11434/v1", "e.g. llama3.1, qwen2.5-coder", true), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      new Preset("LM Studio (local)", "http://localhost:1234/v1", "the model loaded in LM Studio", true), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      new Preset("OpenRouter", "https://openrouter.ai/api/v1", "e.g. anthropic/claude-sonnet-4.5", false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      new Preset("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "e.g. gemini-2.5-flash", false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      new Preset("Groq", "https://api.groq.com/openai/v1", "e.g. llama-3.3-70b-versatile", false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      new Preset("Mistral", "https://api.mistral.ai/v1", "e.g. mistral-large-latest", false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      new Preset("DeepSeek", "https://api.deepseek.com/v1", "e.g. deepseek-chat", false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      new Preset("Together AI", "https://api.together.xyz/v1", "e.g. meta-llama/Llama-3.3-70B-Instruct-Turbo", false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      new Preset("xAI (Grok)", "https://api.x.ai/v1", "a Grok model name", false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
   };

   private final AiChatConfig config_;
   private final ListBox service_;
   private final FormLabel serviceLabel_;
   private Widget serviceSpacer_;
   private final FormLabel apiKeyLabel_;
   private final Label toolsHelp_;
   private final ListBox provider_;
   private final TextBox model_;
   private final TextBox baseUrl_;
   private final PasswordTextBox apiKey_;
   private final Label keyStatus_;
   private final CheckBox removeKey_;
   private final Label customHelp_;

   private static final AiChatConstants constants_ = GWT.create(AiChatConstants.class);
}
