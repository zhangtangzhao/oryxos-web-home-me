package com.oryxos.cli;

import com.oryxos.kb.DefaultKbService;
import com.oryxos.kb.EmbeddingNotConfiguredException;
import com.oryxos.kb.EmbeddingUnavailableException;
import com.oryxos.kb.KbConflictException;
import com.oryxos.kb.KbDocumentRecord;
import com.oryxos.kb.KbDocumentStatus;
import com.oryxos.kb.KbEvalService;
import com.oryxos.kb.KbIngestService;
import com.oryxos.kb.KbNotFoundException;
import com.oryxos.kb.KbRecord;
import org.springframework.beans.factory.annotation.Autowired;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * oryxos kb — 知识库管理（重命令：经 PicocliSpringFactory 注入 DefaultKbService，
 * 触及 JPA 与嵌入客户端；不进 LIGHT_COMMANDS，contracts/cli.md）。
 */
@Command(name = "kb",
         description = "Manage knowledge bases",
         subcommands = {
             KbCommand.CreateCommand.class,
             KbCommand.ListCommand.class,
             KbCommand.ShowCommand.class,
             KbCommand.AddCommand.class,
             KbCommand.IngestCommand.class,
             KbCommand.DeleteCommand.class,
             KbCommand.EvalCommand.class
         })
public class KbCommand {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    @Command(name = "create", description = "创建知识库")
    public static class CreateCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "知识库名称")
        private String name;

        @Option(names = {"--description"}, description = "描述")
        private String description;

        @Autowired
        private DefaultKbService kbService;

        @Override
        public Integer call() {
            try {
                KbRecord record = kbService.create(name, description);
                System.out.println("已创建 " + record.name() + "（路径 " + kbService.kbDir(name) + "）");
                return 0;
            } catch (IllegalArgumentException | KbConflictException e) {
                System.err.println(e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "list", description = "列出全部知识库")
    public static class ListCommand implements Callable<Integer> {

        @Autowired
        private DefaultKbService kbService;

        @Override
        public Integer call() {
            List<KbRecord> kbs = kbService.list();
            if (kbs.isEmpty()) {
                System.out.println("（无知识库）");
                return 0;
            }
            System.out.printf("%-24s %-14s %-22s %-16s%n", "NAME", "DOCS(ready/failed)", "EMBEDDING", "UPDATED");
            for (KbRecord kb : kbs) {
                List<KbDocumentRecord> docs = kbService.documents(kb.name());
                long ready = docs.stream().filter(d -> d.status() == KbDocumentStatus.READY).count();
                long failed = docs.stream().filter(d -> d.status() == KbDocumentStatus.FAILED).count();
                String embedding = kb.embeddingModel() == null
                        ? "-"
                        : kb.embeddingModel() + "(" + kb.embeddingDimensions() + ")";
                String updated = kb.updatedAt() == null ? "-" : TS.format(kb.updatedAt());
                System.out.printf("%-24s %-14s %-22s %-16s%n",
                        kb.name(), ready + "/" + failed, embedding, updated);
            }
            return 0;
        }
    }

    @Command(name = "show", description = "查看知识库状态详情")
    public static class ShowCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "知识库名称")
        private String name;

        @Autowired
        private DefaultKbService kbService;

        @Override
        public Integer call() {
            try {
                KbRecord kb = kbService.require(name);
                List<KbDocumentRecord> docs = kbService.documents(name);
                System.out.println("kb=" + kb.name()
                        + (kb.description() == null || kb.description().isBlank()
                                ? "" : "（" + kb.description() + "）"));
                if (kb.embeddingModel() != null) {
                    System.out.println("embedding: " + kb.embeddingModel() + " (" + kb.embeddingDimensions() + ")");
                }
                System.out.printf("%-28s %-8s %-6s %s%n", "DOC", "STATUS", "CHUNKS", "ERROR");
                for (KbDocumentRecord d : docs) {
                    System.out.printf("%-28s %-8s %-6s %s%n",
                            d.docPath(), d.status(),
                            d.chunkCount() == null ? "-" : d.chunkCount(),
                            d.errorMessage() == null ? "" : d.errorMessage());
                }
                return 0;
            } catch (KbNotFoundException e) {
                System.err.println(e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "add", description = "把文件复制入知识库 docs/ 并记 pending")
    public static class AddCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "知识库名称")
        private String name;

        @Parameters(index = "1..*", arity = "1..*", description = "文档文件（.md/.markdown/.txt）")
        private java.util.List<String> files;

        @Autowired
        private DefaultKbService kbService;

        @Override
        public Integer call() {
            try {
                kbService.require(name);
            } catch (KbNotFoundException e) {
                System.err.println(e.getMessage() + "（先执行: oryxos kb create " + name + "）");
                return 1;
            }
            int added = 0;
            for (String file : files) {
                Path source = Path.of(file);
                try {
                    KbDocumentRecord doc = kbService.addDocument(name, source);
                    System.out.println("已添加: " + doc.docPath() + "（pending）");
                    added++;
                } catch (IllegalArgumentException | KbConflictException e) {
                    System.err.println(e.getMessage());
                    return 1;
                }
            }
            System.out.println("共添加 " + added + " 篇，执行 oryxos kb ingest " + name + " 开始摄取");
            return 0;
        }
    }

    @Command(name = "ingest", description = "执行增量摄取（只处理变化文档）")
    public static class IngestCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "知识库名称")
        private String name;

        @Autowired
        private DefaultKbService kbService;

        @Override
        public Integer call() {
            KbIngestService.IngestSummary summary;
            try {
                summary = kbService.ingest(name);
            } catch (KbNotFoundException e) {
                System.err.println(e.getMessage());
                return 1;
            } catch (KbConflictException e) {
                System.err.println(e.getMessage());
                if (KbConflictException.EMBEDDING_MISMATCH.equals(e.getCode())) {
                    System.err.println("请删除重建或恢复嵌入配置 (" + e.getCode() + ")");
                }
                return 1;
            } catch (EmbeddingNotConfiguredException | EmbeddingUnavailableException e) {
                System.err.println(e.getMessage());
                System.err.println("(" + (e instanceof EmbeddingNotConfiguredException
                        ? "EMBEDDING_NOT_CONFIGURED"
                        : "EMBEDDING_UNAVAILABLE") + ")，文档保留 pending，恢复后可重试");
                return 1;
            }
            System.out.printf("处理 %d / 跳过 %d / 移除 %d / 失败 %d，耗时 %.1fs%n",
                    summary.processed(), summary.skipped(), summary.removed(), summary.failed(),
                    summary.durationMs() / 1000.0);
            for (KbIngestService.DocumentOutcome o : summary.documents()) {
                if (!"ready".equals(o.status()) && !"skipped".equals(o.status())) {
                    System.out.println("  " + o.status() + " " + o.docPath()
                            + (o.error() == null ? "" : "（" + o.error() + "）"));
                }
            }
            return summary.failed() > 0 ? 1 : 0;
        }
    }

    @Command(name = "delete", description = "删除知识库及其全部索引数据与目录")
    public static class DeleteCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "知识库名称")
        private String name;

        @Option(names = {"--yes", "-y"}, description = "跳过交互确认")
        private boolean yes;

        @Autowired
        private DefaultKbService kbService;

        @Override
        public Integer call() {
            try {
                kbService.require(name);
            } catch (KbNotFoundException e) {
                System.err.println(e.getMessage());
                return 1;
            }
            if (!yes) {
                System.out.print("确认删除知识库 " + name + " 及其全部索引数据？(y/N) ");
                String line = readLine();
                if (line == null || !(line.trim().equalsIgnoreCase("y") || line.trim().equalsIgnoreCase("yes"))) {
                    System.out.println("已取消");
                    return 0;
                }
            }
            kbService.delete(name);
            System.out.println("已删除 " + name);
            return 0;
        }

        private String readLine() {
            try {
                return new BufferedReader(new InputStreamReader(System.in)).readLine();
            } catch (Exception e) {
                return null;
            }
        }
    }

    @Command(name = "eval", description = "运行评测集（evalset.yaml），输出 hit@k 报告")
    public static class EvalCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "知识库名称")
        private String name;

        @Option(names = "--top-k", defaultValue = "5", description = "每条样例的检索条数，默认 5")
        private int topK;

        @Autowired
        private KbEvalService evalService;

        @Override
        public Integer call() {
            try {
                KbEvalService.EvalReport report = evalService.eval(name, topK);
                System.out.println(KbEvalService.render(report));
                if (report.hitRate() < 100.0) {
                    System.out.printf("警告: hit@%d 低于 100%%，请检查评测集或检索质量%n", report.topK());
                }
                return 0;
            } catch (KbNotFoundException | IllegalArgumentException | IllegalStateException e) {
                System.err.println(e.getMessage());
                return 1;
            }
        }
    }
}
