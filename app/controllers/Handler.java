package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import model.*;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.Actions;
import services.Tfs;
import services.TgApi;

import javax.inject.Inject;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static utils.TextUtils.getInt;
import static utils.TextUtils.notNull;

/**
 * @author Denis Danilin | denis@danilin.name
 * 16.05.2020
 * tfs ☭ sweat and blood
 */
public class Handler extends Controller {
    private static final Logger.ALogger logger = Logger.of(Handler.class);


    @Inject
    private TgApi api;

    @Inject
    private Tfs tfs;

    @Inject
    private Actions actions;

    public Result get() {
        return ok();
    }

    public Result post(final Http.Request request) {
        try {
            final JsonNode js;
            if (request.hasBody() && (js = request.body().asJson()) != null)
                CompletableFuture.runAsync(() -> handleJson(js))
                        .exceptionally(e -> {
                            logger.error(e.getMessage(), e);
                            return null;
                        });
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
        }

        return ok();
    }

    private void handleJson(final JsonNode js) {
        final User user;
        final Consumer<User> task;

        if (js.has("callback_query")) {
            api.sendCallbackAnswer("", js.get("callback_query").get("id").asLong(), false, 0);
            final String cb = js.get("callback_query").get("data").asText();
            user = getUser(js.get("callback_query").get("from"));

            final int del = cb.indexOf(':');

            if (del < 1) {
                logger.debug("Неизвестный науке коллбек: " + cb);
                task = u -> actions.doView(u);
            } else
                task = u -> actions.onCallback(new Command(CommandType.ofString(cb), del < cb.length() - 1 ? getInt(cb.substring(del + 1)) : -1), u);
        } else if (js.has("message")) {
            CompletableFuture.runAsync(() -> api.deleteMessage(js.get("message").get("message_id").asLong(), js.get("message").get("from").get("id").asLong()));

            final JsonNode msg = js.get("message");

            final String text = msg.has("text") ? msg.get("text").asText() : null;
            user = getUser(msg.get("from"));

            if (msg.has("forward_from") && user.mode == User.Mode.ShareGranter) {
                final TFile file = new TFile();
                file.type = ContentType.CONTACT;
                final JsonNode c = msg.get("forward_from");
                file.setOwner(c.get("id").asLong());
                final String f = c.has("first_name") ? c.get("first_name").asText() : "";
                final String nick = c.has("username") ? c.get("username").asText() : "";
                file.name = notNull(f, notNull(nick, "u" + file.getOwner()));

                task = u -> actions.onFile(file, u);
            } else if (text != null) {
                if (text.equals("/start"))
                    task = u -> {
//                        actions.doView(u);
//                        api.dialogUnescaped(u.helpValue(), u, TgApi.voidKbd);
                        tfs.viewDir(u);
                    };
                else if (text.equals("/help"))
                    task = u -> api.dialogUnescaped(u.helpValue(), u, TgApi.voidKbd);
                else if (text.startsWith("/start shared-"))
                    task = u -> {
                        tfs.joinShare(notNull(text).substring(14), u);
                        actions.doView(u);
                    };
                else
                    task = u -> actions.onInput(text, u);
            } else {
                final JsonNode attachNode;
                final TFile file = new TFile();

                if (msg.has("photo") && msg.get("photo").size() > 0) {
                    if (msg.get("photo").size() == 1)
                        attachNode = msg.get("photo").get(0);
                    else {
                        final TreeMap<Long, JsonNode> map = new TreeMap<>();

                        for (int i = 0; i < msg.get("photo").size(); i++)
                            map.put(msg.get("photo").get(i).get("file_size").asLong(), msg.get("photo").get(i));

                        attachNode = map.lastEntry().getValue();
                    }
                    file.type = ContentType.PHOTO;
                } else if (msg.has("video")) {
                    attachNode = msg.get("video");
                    file.type = ContentType.VIDEO;
                } else if (msg.has("document")) {
                    attachNode = msg.get("document");
                    file.name = attachNode.get("file_name").asText();
                    file.type = ContentType.DOCUMENT;
                } else if (msg.has("audio")) {
                    attachNode = msg.get("audio");
                    file.type = ContentType.AUDIO;
                } else if (msg.has("voice")) {
                    attachNode = msg.get("voice");
                    file.type = ContentType.VOICE;
                } else if (msg.has("sticker")) {
                    attachNode = msg.get("sticker");
                    file.type = ContentType.STICKER;
                } else if (msg.has("contact")) {
                    attachNode = msg.get("contact");
                    file.type = ContentType.CONTACT;

                    // dirty simple hack :)
                    final JsonNode c = msg.get("contact");
                    file.setOwner(c.get("user_id").asLong());
                    final String f = c.has("first_name") ? c.get("first_name").asText() : "";
                    final String l = c.has("last_name") ? c.get("last_name").asText() : "";
                    final String u = c.has("username") ? c.get("username").asText() : "";
                    final String p = c.has("phone_number") ? c.get("phone_number").asText() : "";
                    file.uniqId = msg.has("file_unique_id") ? msg.get("file_unique_id").asText() : p;
                    file.refId = msg.has("file_id") ? msg.get("file_id").asText() : p;
                    file.name = notNull((notNull(f) + " " + notNull(l)), notNull(u, notNull(p, "u" + c.get("user_id").asText())));
                } else {
                    file.type = null;
                    logger.debug("Необслуживаемый тип сообщения");
                    return;
                }

                if (file.type != null && attachNode != null) {
                    if (file.refId == null) file.refId = attachNode.get("file_id").asText();
                    if (file.uniqId == null) file.uniqId = attachNode.get("file_unique_id").asText();

                    if (file.name == null)
                        file.name = msg.has("caption") && !msg.get("caption").asText().trim().isEmpty()
                                ? msg.get("caption").asText().trim()
                                : file.type.name().toLowerCase() + "_" + file.uniqId;

                    if (file.type == ContentType.CONTACT)
                        file.refId = attachNode.toString();

                    task = u -> actions.onFile(file, u);
                } else
                    task = u -> actions.doView(u);
            }
        } else {
            logger.debug("Необслуживаемый тип сообщения");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                task.accept(user);
            } finally {
                tfs.update(user);
            }
        }).exceptionally(e -> {
            logger.error("Handling input [" + js + "]: " + e.getMessage(), e);
            return null;
        });
    }

    private User getUser(final JsonNode node) {
        final String f = node.has("first_name") ? node.get("first_name").asText() : null;
        final String l = node.has("last_name") ? node.get("last_name").asText() : null;
        final String n = node.has("username") ? node.get("username").asText() : null;
        final long id = node.get("id").asLong();

        try {
            return tfs.getUser(id,
                    node.has("language_code") ? node.get("language_code").asText() : "en",
                    notNull((notNull(f) + " " + notNull(l)), notNull(n, "u" + id)));
        } finally {
            api.cleanup(id);
        }
    }
}
