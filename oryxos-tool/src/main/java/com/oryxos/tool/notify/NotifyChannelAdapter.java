package com.oryxos.tool.notify;

/**
 * Notification channel abstraction (constitution IX: interface first).
 * Channels are registered globally and referenced by name from Agent
 * frontmatter or tool arguments; the MVP ships the generic webhook channel
 * (clarification #1).
 */
public interface NotifyChannelAdapter {

    /** Channel identifier referenced from configuration/arguments. */
    String getName();

    /**
     * Push a notification message.
     *
     * @param target  channel-specific endpoint (e.g. webhook URL)
     * @param message notification text
     * @return delivery result description
     * @throws Exception on delivery failure (caller decides retryability)
     */
    String send(String target, String message) throws Exception;
}
