package io.github.bakwudo.uyu.extension.settings;

import android.app.Activity;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.view.View;
import android.widget.TextView;

@SuppressWarnings("deprecation")
public class UyuSettingsFragment extends PreferenceFragment {
    static final String SECTION_ADS = "ads";
    static final String SECTION_EMOTES = "emotes";
    static final String SECTION_CHAT = "chat";
    static final String SECTION_PRIVACY = "privacy";

    private static final String ARG_SECTION = "section";
    private String section;
    private CharSequence previousTitle;

    static UyuSettingsFragment create(String section) {
        UyuSettingsFragment fragment = new UyuSettingsFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_SECTION, section);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(Setting.PREFERENCES_NAME);

        Bundle arguments = getArguments();
        section = arguments == null ? null : arguments.getString(ARG_SECTION);

        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getActivity());
        setPreferenceScreen(screen);

        if (section == null) addSectionLinks(screen);
        else if (SECTION_ADS.equals(section)) addAdsSettings(screen);
        else if (SECTION_EMOTES.equals(section)) addEmoteSettings(screen);
        else if (SECTION_CHAT.equals(section)) addChatSettings(screen);
        else if (SECTION_PRIVACY.equals(section)) addPrivacySettings(screen);
    }

    private void addSectionLinks(PreferenceScreen screen) {
        addSectionLink(screen, SECTION_ADS, "Ad blocking",
                "Live ad blocking through Kizu's manifest proxy.");
        addSectionLink(screen, SECTION_EMOTES, "7TV, BTTV, FFZ and animated emotes",
                "Third-party emotes in live chat.");
        addSectionLink(screen, SECTION_CHAT, "Chat controls",
                "Current Kizu chat controls.");
        addSectionLink(screen, SECTION_PRIVACY, "Privacy",
                "Disable Twitch measurement and crash reporting components.");
    }

    private void addSectionLink(
            PreferenceScreen screen,
            String linkedSection,
            String title,
            String summary
    ) {
        Preference preference = new Preference(screen.getContext());
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setOnPreferenceClickListener(clicked -> {
            Activity activity = getActivity();
            if (activity != null) SettingsPatch.showScreen(activity, linkedSection);
            return true;
        });
        screen.addPreference(preference);
    }

    private void addAdsSettings(PreferenceScreen screen) {
        addSwitch(screen, Settings.BLOCK_ADS, "Block ads",
                "Uses Kizu's live manifest proxy. Turn this off to restore normal ad behavior.");
    }

    private void addEmoteSettings(PreferenceScreen screen) {
        addSwitch(screen, Settings.EMOTES_7TV, "7TV emotes",
                "Show global and channel 7TV emotes in live chat.");
        addSwitch(screen, Settings.EMOTES_BTTV, "BTTV emotes",
                "Show global and channel BetterTTV emotes in live chat.");
        addSwitch(screen, Settings.EMOTES_FFZ, "FFZ emotes",
                "Show global and channel FrankerFaceZ emotes in live chat.");
        addSwitch(screen, Settings.EMOTES_ANIMATED, "Animated emotes",
                "Play animated third-party emotes. Turn this off to render a static frame.");
    }

    private void addChatSettings(PreferenceScreen screen) {
        addSwitch(screen, Settings.HIDE_CHAT_BITS_BUTTON, "Hide Bits button",
                "Hide the Bits button beside Twitch's chat controls.");
    }

    private void addPrivacySettings(PreferenceScreen screen) {
        addSwitch(screen, Settings.DISABLE_COMSCORE, "Disable Comscore",
                "Prevent Twitch's Comscore measurement component from starting. Restart Twitch after changing this.");
        addSwitch(screen, Settings.DISABLE_BUGSNAG, "Disable crash reporting",
                "Prevent Twitch 31.3.1's Firebase Crashlytics crash reporting from collecting reports. Restart Twitch after changing this.");
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(SettingsUi.backgroundColor(view.getContext()));
        view.setClickable(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = getActivity();
        TextView title = activity == null ? null : SettingsPatch.findToolbarTitle(activity);
        if (title == null) return;
        if (previousTitle == null) previousTitle = title.getText();
        title.setText(title(section));
    }

    @Override
    public void onDestroyView() {
        Activity activity = getActivity();
        TextView title = activity == null ? null : SettingsPatch.findToolbarTitle(activity);
        if (title != null && previousTitle != null) title.setText(previousTitle);
        super.onDestroyView();
    }

    private static String title(String section) {
        if (section == null) return SettingsPatch.TITLE;
        switch (section) {
            case SECTION_ADS: return "Ads";
            case SECTION_EMOTES: return "Emotes";
            case SECTION_CHAT: return "Chat";
            case SECTION_PRIVACY: return "Privacy";
            default: return SettingsPatch.TITLE;
        }
    }

    private static SwitchPreference addSwitch(
            PreferenceGroup group,
            BooleanSetting setting,
            String title,
            String summary
    ) {
        SwitchPreference preference = new SwitchPreference(group.getContext());
        preference.setKey(setting.key);
        preference.setDefaultValue(setting.defaultValue);
        preference.setTitle(title);
        preference.setSummary(summary);
        group.addPreference(preference);
        return preference;
    }
}
