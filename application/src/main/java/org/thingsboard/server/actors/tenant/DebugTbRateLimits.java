package org.thingsboard.server.actors.tenant;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.thingsboard.server.common.msg.tools.TbRateLimits;

/**
 * 用于规则引擎debug的限流策略
 */
@Data
@AllArgsConstructor
public class DebugTbRateLimits {

    private TbRateLimits tbRateLimits;

    // 规则链是否保存
    private boolean ruleChainEventSaved;

}
