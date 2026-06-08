package cn.rmc.slimefunweaver.web;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ResearchApiJsonTest {

    @Test
    public void parsesResearchPayloadWithWhitespaceAndDecimalMoneyCost() {
        String json = "{ \"researches\" : [ { \"fullKey\" : \"addon:custom_research\", \"levelCost\" : 7, \"moneyCost\" : 12.5, \"enabled\" : true, \"needUnlockedItems\" : [\"ITEM_A\", \"ITEM\\nB\"], \"miningLevelNeed\" : 3 } ] }";

        List<ResearchApiHandler.ResearchUpdate> parsed = ResearchApiHandler.parseResearchSavePayload(json);

        assertEquals(1, parsed.size());
        ResearchApiHandler.ResearchUpdate update = parsed.get(0);
        assertEquals("addon:custom_research", update.fullKey);
        assertEquals(Integer.valueOf(7), update.levelCost);
        assertEquals(Double.valueOf(12.5), update.moneyCost);
        assertEquals(Boolean.TRUE, update.enabled);
        assertEquals("ITEM\nB", update.needUnlockedItems.get(1));
        assertEquals(Integer.valueOf(3), update.skillLevels.get("miningLevelNeed"));
    }
}
