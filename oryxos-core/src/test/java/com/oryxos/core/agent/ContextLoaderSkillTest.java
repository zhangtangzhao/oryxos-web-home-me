package com.oryxos.core.agent;

import com.oryxos.core.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Constitution VIII / FR-005: Skill visibility comes from the agent's skills/
 * directory; only name/description metadata enters the system prompt and the
 * body is referenced by read path — never registered as a tool.
 */
class ContextLoaderSkillTest {

    @TempDir
    Path workspace;

    @Test
    void skillMetadataListsNameDescriptionAndReadPathOnly() throws Exception {
        Path skillDir = workspace.resolve("agents").resolve("demo").resolve("skills").resolve("news-report");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), String.join("\n",
                "---",
                "name: news-report",
                "description: 每日科技新闻汇总方法",
                "---",
                "",
                "这里是技能正文，属于按需读取内容。"));

        ContextLoader loader = new ContextLoader(workspace);
        Profile profile = new Profile();
        profile.setName("demo");

        String metadata = loader.skillMetadata(profile);

        assertTrue(metadata.contains("news-report"));
        assertTrue(metadata.contains("每日科技新闻汇总方法"));
        assertTrue(metadata.contains("SKILL.md"), "元数据必须给出正文读取路径");
        assertFalse(metadata.contains("技能正文"), "正文不得进入 system prompt（宪法 VIII）");
    }

    @Test
    void missingSkillsDirectoryYieldsEmptyMetadata() {
        Profile profile = new Profile();
        profile.setName("nobody");
        assertTrue(new ContextLoader(workspace).skillMetadata(profile).isEmpty());
    }

    @Test
    void truncateAppendsMarker() {
        String out = ContextLoader.truncate("x".repeat(50), 10);
        assertTrue(out.startsWith("xxxxxxxxxx"));
        assertTrue(out.contains("截断"));
    }
}
