package model;

import utils.LangMap;

/**
 * @author Denis Danilin | denis@danilin.name
 * 17.06.2020
 * tfs ☭ sweat and blood
 */
public class User {

    public long id;
    public String lang;
    public String name;

    public long windowId, dialogId;
    public String path;

    public Mode mode;

    public long skip;

    public User(final long id,
                final String lang,
                final String name, final String path) {
        this.id = id;
        this.lang = lang;
        this.name = name;
        this.path = path;

        mode = Mode.RootViewer;
    }

    public LangMap.Value helpValue() {
        return mode.helpValue;
    }

    public enum Mode {
        RootViewer(LangMap.Value.ROOT_HELP), ShareGranter(LangMap.Value.SHARE_DIR_HELP);
        public final LangMap.Value helpValue;

        Mode(final LangMap.Value helpValue) {this.helpValue = helpValue;}
    }
}
