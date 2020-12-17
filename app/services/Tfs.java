package services;

import com.typesafe.config.Config;
import model.CommandType;
import model.ParseMode;
import model.User;
import play.Logger;
import play.libs.Json;
import utils.Strings;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Objects;

@Singleton
public class Tfs {
    private static final Logger.ALogger logger = Logger.of(Tfs.class);

    public final String _root, _home, _shares;
    public final Path root, home, shares;

    private final String meta;

    @Inject
    private TgApi api;

    @Inject
    public Tfs(final Config config) {
        _root = config.getString("service.sys_path");
        _home = config.hasPath("service.home_dir") ? config.getString("service.home_dir") : "home";
        _shares = config.hasPath("service.shares_dir") ? config.getString("service.shares_dir") : "shares";
        meta = config.hasPath("service.meta_file_name") ? config.getString("service.meta_file_name") : ".tdata";

        root = Paths.get(_root);
        home = root.resolve(_home);
        shares = root.resolve(_shares);

        mkdir(home);
        mkdir(shares);
    }

    public User getUser(final long id, final String lang, final String name) {
        final Path userHome = home.resolve(String.valueOf(id));

        mkdir(userHome);
        User user = readUser(userHome);

        if (user != null)
            return user;

        user = new User(id, lang, name, userHome.toString());
        writeUser(user, userHome);

        return user;
    }

    private void writeUser(final User user, final Path userHome) {
        final Path metaFile = userHome.resolve(meta);
        try {
            Files.writeString(metaFile, Json.toJson(user).toString(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        } catch (IOException e) {
            logger.error(metaFile + ": " + e.getMessage(), e);
        }
    }

    private User readUser(final Path userHome) {
        final Path metaFile = userHome.resolve(meta);

        if (Files.notExists(metaFile))
            return null;

        try {
            return Json.fromJson(Json.parse(Files.readAllBytes(metaFile)), User.class);
        } catch (final Exception e) {
            logger.error(metaFile + ": " + e.getMessage(), e);
        }

        return null;
    }

    private void mkdir(final Path path) {
        try {
            if (!Files.exists(path))
                Files.createDirectory(path);
        } catch (final Exception e) {
            logger.error(path + ": " + e.getMessage(), e);
        }
    }

    public void update(final User user) {
        writeUser(user, home.resolve(String.valueOf(user.id)));
    }

    public void goHome(final User user) {
        user.path = home.resolve(String.valueOf(user.id)).toString();
        user.mode = User.Mode.RootViewer;
    }

    public void joinShare(final String shareId, final User user) {

    }

    public void viewDir(final User user) {
        final Path cp = home.resolve(user.path);

        final TgApi.Keyboard kbd = initKeyboard();


        try {
            Files.list(cp)
                    .sorted(Comparator.comparing(o -> o.getFileName().toString()))
                    .skip(user.skip)
                    .limit(10)
                    .forEach(p -> {
                        final String s = p.getFileName().toString();
                        kbd.newLine().button(new TgApi.Button((Files.isDirectory(p) ? Strings.Uni.folder + " " : "") + s, s));
                    });
        } catch (IOException e) {
            logger.error(user.path + ": " + e.getMessage(), e);
        }


        final int count = Objects.requireNonNull(cp.toFile().list()).length;

        if (user.skip > 0 || count > 10) {
            kbd.newLine();

            if (user.skip > 0)
                kbd.button(CommandType.rewind.b());
            if ((count - user.skip) > 10)
                kbd.button(CommandType.forward.b());
        }

        api.sendContent(null, initBody(count == 0), ParseMode.md2, kbd, user);

    }

    protected TgApi.Keyboard initKeyboard() {
        final TgApi.Keyboard kbd = new TgApi.Keyboard();

        if (isDeep())
            kbd.button(CommandType.openParent.b());
        if (dir.isRw()) {
            kbd.button(CommandType.mkLabel.b());
            kbd.button(CommandType.mkDir.b());
            kbd.button(CommandType.gear.b());
        }

        return kbd;
    }
}
