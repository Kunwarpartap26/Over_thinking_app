package com.tetra.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

public class ThemeManager {

    private static final String PREFS = "tetra_prefs";
    private static final String KEY   = "app_theme";

    public static final String VAULT       = "vault";
    public static final String MIDNIGHT    = "midnight";
    public static final String MONSOON     = "monsoon";
    public static final String TERRACOTTA  = "terracotta";
    public static final String CLARITY     = "clarity";

    /** Call this BEFORE setContentView() in every Activity */
    public static void apply(Activity activity) {
        switch (getSaved(activity)) {
            case MIDNIGHT:    activity.setTheme(R.style.Theme_Midnight);    break;
            case MONSOON:     activity.setTheme(R.style.Theme_Monsoon);     break;
            case TERRACOTTA:  activity.setTheme(R.style.Theme_Terracotta);  break;
            case CLARITY:     activity.setTheme(R.style.Theme_Clarity);     break;
            default:          activity.setTheme(R.style.Theme_Vault);       break;
        }
    }

    public static void save(Context ctx, String theme) {
        prefs(ctx).edit().putString(KEY, theme).apply();
    }

    public static String getSaved(Context ctx) {
        return prefs(ctx).getString(KEY, VAULT);
    }

    public static boolean isSet(Context ctx) {
        return prefs(ctx).contains(KEY);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
