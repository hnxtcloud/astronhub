package com.iflytek.skillhub.controller.portal;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.controller.portal.SoftwareRangeParser.RangeResult;
import org.junit.jupiter.api.Test;

class SoftwareRangeParserTest {

    private static final long SIZE = 1000;

    @Test
    void missingOrMalformedHeadersAreIgnored() {
        assertThat(RangeResult.Ignored.class.cast(parser(null))).isNotNull();
        assertThat(parser("bytes=")).isInstanceOf(RangeResult.Ignored.class);
        assertThat(parser("chunks=0-10")).isInstanceOf(RangeResult.Ignored.class);
        assertThat(parser("bytes=abc-def")).isInstanceOf(RangeResult.Ignored.class);
        assertThat(parser("bytes=0")).isInstanceOf(RangeResult.Ignored.class);
    }

    @Test
    void multiRangeIsDowngradedToFullResponse() {
        assertThat(parser("bytes=0-10,20-30")).isInstanceOf(RangeResult.Ignored.class);
    }

    @Test
    void simpleAndOpenRangesAreParsedWithEndClamped() {
        RangeResult.Single both = (RangeResult.Single) parser("bytes=100-199");
        assertThat(both.start()).isEqualTo(100);
        assertThat(both.end()).isEqualTo(199);

        RangeResult.Single open = (RangeResult.Single) parser("bytes=900-");
        assertThat(open.start()).isEqualTo(900);
        assertThat(open.end()).isEqualTo(999);

        RangeResult.Single clamped = (RangeResult.Single) parser("bytes=900-99999");
        assertThat(clamped.end()).isEqualTo(999);
    }

    @Test
    void suffixRangeTakesTheLastNBytes() {
        RangeResult.Single suffix = (RangeResult.Single) parser("bytes=-100");
        assertThat(suffix.start()).isEqualTo(900);
        assertThat(suffix.end()).isEqualTo(999);

        RangeResult.Single oversized = (RangeResult.Single) parser("bytes=-99999");
        assertThat(oversized.start()).isEqualTo(0);
        assertThat(oversized.end()).isEqualTo(999);
    }

    @Test
    void unsatisfiableRangesReturn416Signal() {
        assertThat(parser("bytes=1000-")).isInstanceOf(RangeResult.Unsatisfiable.class);
        assertThat(parser("bytes=1500-1600")).isInstanceOf(RangeResult.Unsatisfiable.class);
        assertThat(parser("bytes=-0")).isInstanceOf(RangeResult.Unsatisfiable.class);
    }

    @Test
    void invertedRangeIsIgnoredRatherThanErroring() {
        assertThat(parser("bytes=500-100")).isInstanceOf(RangeResult.Ignored.class);
    }

    @Test
    void emptyObjectSuffixIsIgnored() {
        assertThat(SoftwareRangeParser.parse("bytes=-10", 0)).isInstanceOf(RangeResult.Ignored.class);
        assertThat(SoftwareRangeParser.parse("bytes=0-", 0)).isInstanceOf(RangeResult.Unsatisfiable.class);
    }

    private static RangeResult parser(String header) {
        return SoftwareRangeParser.parse(header, SIZE);
    }
}
