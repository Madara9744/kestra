package io.kestra.core.tasks.storages;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.List;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    description = "Concat files from internal storage."
)
@Plugin(
    examples = {
        @Example(
            title = "Concat 2 files with a custom separator",
            code = {
                "files: ",
                "  - \"kestra://long/url/file1.txt\"",
                "  - \"kestra://long/url/file2.txt\"",
                "separator: \"\\n\"",
            }
        ),
        @Example(
            title = "Concat file generated by a each tasks",
            code = {
                "tasks:",
                "  - id: each",
                "    type: io.kestra.core.tasks.flows.EachSequential",
                "    tasks:",
                "      - id: start_api_call",
                "        type: io.kestra.core.tasks.scripts.Bash",
                "        commands:",
                "          - echo {{ taskrun.value }} > {{ temp.generated }}",
                "        files:",
                "          - generated",
                "    value: '[\"value1\", \"value2\", \"value3\"]'",
                "  - id: concat",
                "    type: io.kestra.core.tasks.storages.Concat",
                "    files:",
                "      - \"{{ outputs.start_api_call.value1.files.generated }}\"",
                "      - \"{{ outputs.start_api_call.value2.files.generated }}\"",
                "      - \"{{ outputs.start_api_call.value3.files.generated }}\"",
            },
            full = true
        )
    }
)
public class Concat extends Task implements RunnableTask<Concat.Output> {
    @Schema(
        title = "List of files to be concatenated.",
        description = "Must be a `kestra://` storage url"
    )
    @PluginProperty(dynamic = true)
    private List<String> files;

    @Schema(
        title = "The separator to used between files, default is no seprator"
    )
    @PluginProperty(dynamic = true)
    private String separator;

    @Override
    public Concat.Output run(RunContext runContext) throws Exception {
        File tempFile = File.createTempFile("concat_", "");
        try (FileOutputStream fileOutputStream = new FileOutputStream(tempFile)) {
            if (files != null) {
                files.forEach(throwConsumer(s -> {
                    URI from = new URI(runContext.render(s));
                    IOUtils.copyLarge(runContext.uriToInputStream(from), fileOutputStream);

                    if (separator != null) {
                        IOUtils.copy(new ByteArrayInputStream(this.separator.getBytes()), fileOutputStream);
                    }
                }));
            }
        }

        return Concat.Output.builder()
            .uri(runContext.putTempFile(tempFile))
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The concatenate file uri."
        )
        private final URI uri;
    }
}