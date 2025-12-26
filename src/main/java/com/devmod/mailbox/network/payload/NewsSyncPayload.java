package com.devmod.mailbox.network.payload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload to sync news articles from server to client.
 */
public record NewsSyncPayload(
    List<NewsArticleData> articles,
    int unreadCount
) implements CustomPacketPayload {

    // Security limits
    private static final int MAX_ARTICLES = 100;
    private static final int MAX_TITLE_LENGTH = 256;
    private static final int MAX_CONTENT_LENGTH = 10000;
    private static final int MAX_NAME_LENGTH = 64;

    public static final Type<NewsSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "news_sync"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, NewsSyncPayload> STREAM_CODEC = StreamCodec.of(
        NewsSyncPayload::encode,
        NewsSyncPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, NewsSyncPayload payload) {
        buf.writeVarInt(payload.articles.size());
        for (NewsArticleData article : payload.articles) {
            encodeArticle(buf, article);
        }
        buf.writeVarInt(payload.unreadCount);
    }

    private static NewsSyncPayload decode(RegistryFriendlyByteBuf buf) {
        int articleCount = buf.readVarInt();
        if (articleCount < 0 || articleCount > MAX_ARTICLES) {
            articleCount = 0;
        }

        List<NewsArticleData> articles = new ArrayList<>(articleCount);
        for (int i = 0; i < articleCount; i++) {
            articles.add(decodeArticle(buf));
        }

        int unreadCount = buf.readVarInt();

        return new NewsSyncPayload(articles, unreadCount);
    }

    private static void encodeArticle(RegistryFriendlyByteBuf buf, NewsArticleData article) {
        buf.writeUUID(Objects.requireNonNull(article.id));
        buf.writeUtf(Objects.requireNonNull(article.title));
        buf.writeUtf(Objects.requireNonNull(article.content));
        buf.writeVarInt(article.categoryOrdinal);
        buf.writeUtf(article.authorName != null ? article.authorName : "");
        buf.writeLong(article.publishedAtMillis);
        buf.writeVarInt(article.priority);
        buf.writeBoolean(article.isRead);
    }

    private static NewsArticleData decodeArticle(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String title = buf.readUtf(MAX_TITLE_LENGTH);
        String content = buf.readUtf(MAX_CONTENT_LENGTH);
        int categoryOrdinal = buf.readVarInt();
        String authorName = buf.readUtf(MAX_NAME_LENGTH);
        long publishedAtMillis = buf.readLong();
        int priority = buf.readVarInt();
        boolean isRead = buf.readBoolean();

        // Convert empty string to null
        if (authorName.isEmpty()) authorName = null;

        return new NewsArticleData(id, title, content, categoryOrdinal, authorName, publishedAtMillis, priority, isRead);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Create an empty sync payload.
     */
    public static NewsSyncPayload empty() {
        return new NewsSyncPayload(List.of(), 0);
    }

    /**
     * Data structure for a single news article in the sync.
     */
    public record NewsArticleData(
        UUID id,
        String title,
        String content,
        int categoryOrdinal,
        @Nullable String authorName,
        long publishedAtMillis,
        int priority,
        boolean isRead
    ) {
        /**
         * Get the category display name.
         */
        public String getCategoryName() {
            // Map ordinal to category names
            return switch (categoryOrdinal) {
                case 0 -> "Patch Notes";
                case 1 -> "Events";
                case 2 -> "Announcements";
                case 3 -> "Maintenance";
                case 4 -> "Dev Blog";
                case 5 -> "Community";
                default -> "News";
            };
        }
    }
}
