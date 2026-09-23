package com.zx.chat.agent.service.infra.time;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UTC 时间契约的守卫测试。
 *
 * <p>全项目约定「DB 存 UTC、Java 侧一律按 UTC 解读」，这个约定没有编译期保障，
 * 只能靠纪律。本测试把纪律固化：扫描生产代码里的无参 {@code LocalDateTime.now()}，
 * 并检查 application.yml 里两处决定时区语义的配置没被改掉。
 */
class UtcTimeGuardTest {

    /** 违规写法：无参 now() 取的是 JVM 本地时间 */
    private static final String BARE_NOW = "LocalDateTime.now()";

    private static final List<String> MODULES = List.of(
            "chat-agent-api",
            "chat-agent-service",
            "chat-agent-provider");

    /**
     * 判断一段源码是否出现了无参 now()。
     *
     * <p>合法写法 {@code LocalDateTime.now(ZoneOffset.UTC)} 括号里有参数，
     * 不会命中 {@code "LocalDateTime.now()"} 这个完整字面量。
     *
     * <p>注释行会被跳过：文档里说明「禁止这么写」时必然要原样写出被禁的写法，
     * 那不算违规。
     */
    static boolean hasBareNow(String source) {
        return source.lines()
                .filter(line -> !isComment(line))
                .anyMatch(line -> line.contains(BARE_NOW));
    }

    /** 粗略识别注释行：行首是 // 、* 或 /* 就当注释 */
    private static boolean isComment(String line) {
        String trimmed = line.strip();
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
    }

    /**
     * 定位仓库根目录。surefire 的工作目录是模块目录，其父目录即仓库根。
     */
    private static Path repoRoot() {
        Path root = Paths.get("").toAbsolutePath().getParent();
        assertThat(root.resolve("pom.xml"))
                .as("没找到仓库根，工作目录可能不是模块目录")
                .exists();
        return root;
    }

    /** 收集三个模块 src/main/java 下的所有 java 文件 */
    private static List<Path> productionSources() {
        Path root = repoRoot();
        List<Path> files = new ArrayList<>();
        for (String module : MODULES) {
            Path dir = root.resolve(module).resolve("src/main/java");
            if (!Files.exists(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return files;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void shouldFlagBareNow() {
        assertThat(hasBareNow("        return LocalDateTime.now();")).isTrue();
    }

    @Test
    void shouldNotFlagUtcNow() {
        assertThat(hasBareNow("        return LocalDateTime.now(ZoneOffset.UTC);")).isFalse();
    }

    @Test
    void shouldNotFlagCommentMention() {
        assertThat(hasBareNow(" * 禁止 LocalDateTime.now()，请改用 UTC 写法")).isFalse();
        assertThat(hasBareNow("        // LocalDateTime.now() 是错的")).isFalse();
    }

    @Test
    void shouldScanNonEmptyFileSet() {
        // 防止扫描路径写错时，违规检查空跑也算通过
        assertThat(productionSources()).isNotEmpty();
    }

    @Test
    void shouldHaveNoBareNowInProductionCode() {
        List<String> violations = productionSources().stream()
                .filter(p -> hasBareNow(read(p)))
                .map(p -> repoRoot().relativize(p).toString())
                .toList();

        assertThat(violations)
                .as("这些文件用了无参 LocalDateTime.now()，改成 LocalDateTime.now(ZoneOffset.UTC)")
                .isEmpty();
    }

    @Test
    void shouldKeepJdbcUrlInUtc() {
        String yml = read(repoRoot()
                .resolve("chat-agent-provider/src/main/resources/application.yml"));

        assertThat(yml).contains("connectionTimeZone=UTC");
    }

    @Test
    void shouldKeepJacksonTimeZoneInUtc() {
        String yml = read(repoRoot()
                .resolve("chat-agent-provider/src/main/resources/application.yml"));

        assertThat(yml).contains("time-zone: UTC");
    }
}
