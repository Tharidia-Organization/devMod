package com.devmod.mailbox.network.payload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Payload to sync news articles from server to client.
 */
public record NewsSyncPayload(
    List<NewsArticleData> articles,
    int unreadCount
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<NewsSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "news_sync"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, NewsSyncPayload> STREAM_CODEC = StreamCodec.of(
        NewsSyncPayload::encode,
        NewsSyncPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, NewsSyncPayload payload) {
        int articleCount = MailboxPayloadLimits.clampCount(payload.articles.size(), MailboxPayloadLimits.MAX_NEWS_ARTICLES);
        buf.writeVarInt(articleCount);
        for (int i = 0; i < articleCount; i++) {
            NewsArticleData article = payload.articles.get(i);
            encodeArticle(buf, article);
        }
        buf.writeVarInt(payload.unreadCount);
    }

    private static NewsSyncPayload decode(RegistryFriendlyByteBuf buf) {
        int articleCount = buf.readVarInt();
        articleCount = MailboxPayloadLimits.clampCount(articleCount, MailboxPayloadLimits.MAX_NEWS_ARTICLES);

        List<NewsArticleData> articles = new ArrayList<>(articleCount);
        for (int i = 0; i < articleCount; i++) {
            articles.add(decodeArticle(buf));
        }

        int unreadCount = buf.readVarInt();

        return new NewsSyncPayload(articles, unreadCount);
    }

    private static void encodeArticle(RegistryFriendlyByteBuf buf, NewsArticleData article) {
        buf.writeUUID(Objects.requireNonNull(article.id));
        buf.writeUtf(
            MailboxPayloadLimits.truncate(article.title, MailboxPayloadLimits.MAX_NEWS_TITLE_LENGTH),
            MailboxPayloadLimits.MAX_NEWS_TITLE_LENGTH
        );
        buf.writeUtf(
            MailboxPayloadLimits.truncate(article.content, MailboxPayloadLimits.MAX_NEWS_CONTENT_LENGTH),
            MailboxPayloadLimits.MAX_NEWS_CONTENT_LENGTH
        );
        buf.writeVarInt(article.categoryOrdinal);
        buf.writeUtf(
            MailboxPayloadLimits.truncate(article.authorName, MailboxPayloadLimits.MAX_NEWS_AUTHOR_LENGTH),
            MailboxPayloadLimits.MAX_NEWS_AUTHOR_LENGTH
        );
        buf.writeLong(article.publishedAtMillis);
        buf.writeVarInt(article.priority);
        buf.writeBoolean(article.isRead);
    }

    private static NewsArticleData decodeArticle(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String title = buf.readUtf(MailboxPayloadLimits.MAX_NEWS_TITLE_LENGTH);
        String content = buf.readUtf(MailboxPayloadLimits.MAX_NEWS_CONTENT_LENGTH);
        int categoryOrdinal = buf.readVarInt();
        String authorName = buf.readUtf(MailboxPayloadLimits.MAX_NEWS_AUTHOR_LENGTH);
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

    @Override
    public int estimatedSize() {
        int count = MailboxPayloadLimits.clampCount(articles.size(), MailboxPayloadLimits.MAX_NEWS_ARTICLES);
        int size = PayloadSizeUtil.varIntSize(count);
        for (int i = 0; i < count; i++) {
            NewsArticleData article = articles.get(i);
            size += estimateArticleSize(article);
        }
        size += PayloadSizeUtil.varIntSize(unreadCount);
        return size;
    }

    private static int estimateArticleSize(NewsArticleData article) {
        int size = 16; // UUID
        size += estimatedUtfSize(article.title, MailboxPayloadLimits.MAX_NEWS_TITLE_LENGTH);
        size += estimatedUtfSize(article.content, MailboxPayloadLimits.MAX_NEWS_CONTENT_LENGTH);
        size += PayloadSizeUtil.varIntSize(article.categoryOrdinal);
        size += estimatedUtfSize(article.authorName, MailboxPayloadLimits.MAX_NEWS_AUTHOR_LENGTH);
        size += 8; // publishedAtMillis
        size += PayloadSizeUtil.varIntSize(article.priority);
        size += 1; // isRead
        return size;
    }

    private static int estimatedUtfSize(@Nullable String value, int maxLength) {
        return PayloadSizeUtil.estimatedUtfSize(MailboxPayloadLimits.truncateNullable(value, maxLength));
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
        @Nonnull
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
