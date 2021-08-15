package org.thingsboard.server.queue.settings;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class TbQueueTransportNotificationSettings {

    /**
     * 接收通知topic
     */
    @Value("${queue.transport.notifications_topic}")
    private String notificationsTopic;

    /**
     * 轮训的间隔
     */
    @Value("${queue.transport.poll_interval}")
    private long transportPollInterval;

}
