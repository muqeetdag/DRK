package android.test;

import android.content.Context;
import android.content.SharedPreferencesImpl;
import android.content.res.Resources;

import java.io.File;

/** Boots the ambient Context globals the framework needs before any Activity exists. */
public final class Boot {

    public static Context CONTEXT;

    private Boot() {}

    public static Context boot(Resources resources, File files, File cache, File external,
                               SharedPreferencesImpl prefs) {
        Context context = new Context();
        context.attach(resources, files, cache, external, prefs);
        CONTEXT = context;
        Env.resRoot = new File(external, "../..").getAbsolutePath();
        return context;
    }
}
