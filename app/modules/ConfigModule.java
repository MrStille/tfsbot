package modules;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.typesafe.config.Config;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(Constants.class).asEagerSingleton();
    }

    @Singleton
    public static class Constants {
        public static Path sysPath;
        public static String sysPathName;

        @Inject
        public Constants(final Config config) {
            sysPathName = config.getString("service.sys_path");
            sysPath = Paths.get(sysPathName);
        }
    }
}
