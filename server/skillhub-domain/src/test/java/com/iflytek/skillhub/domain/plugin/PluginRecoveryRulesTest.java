package com.iflytek.skillhub.domain.plugin;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PluginRecoveryRulesTest {
 @Test void heartbeatKeepsLongWorkOwnedAndExpiredResultsCannotReplaceReclaimedAttempt(){
  var channel=new PluginPublicationChannel(1L,"GIT","approved-plan");var start=Instant.parse("2026-10-15T00:00:00Z");
  String old=channel.claim(start,30);
  assertThat(channel.heartbeat(old,0,start.plusSeconds(20),30)).isTrue();
  assertThat(channel.claim(start.plusSeconds(40),30)).isNull();
  String replacement=channel.claim(start.plusSeconds(51),30);
  assertThat(channel.finish(old,0,start.plusSeconds(52),"stale",null,false)).isFalse();
  assertThat(channel.finish(replacement,0,start.plusSeconds(52),"actual-commit",null,false)).isTrue();
  assertThat(channel.getReceipt()).isEqualTo("actual-commit");
 }
 @Test void explicitReplacementPreservesOriginalPlanAndCannotReplaceSuccessOrUnknownEffects(){
  var channel=new PluginPublicationChannel(1L,"NPM",null);
  channel.replan(0,"explicit-new-target");
  assertThat(channel.getSnapshot()).isEqualTo("MISSING_CONFIGURATION");
  assertThat(channel.getEffectiveSnapshot()).isEqualTo("explicit-new-target");
  assertThat(channel.getPlanRevision()).isEqualTo(1);
  assertThatThrownBy(()->channel.replan(0,"raced-target")).isInstanceOf(RuntimeException.class);
  var now=Instant.parse("2026-10-15T00:00:00Z");String token=channel.claim(now,30);
  channel.finish(token,1,now.plusSeconds(1),null,"UNCONFIRMED",true);
  assertThatThrownBy(()->channel.replan(1,"unsafe-redirect")).isInstanceOf(RuntimeException.class);
 }
}
