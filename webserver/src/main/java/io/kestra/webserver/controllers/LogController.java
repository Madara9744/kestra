package io.kestra.webserver.controllers;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.sse.Event;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;
import org.slf4j.event.Level;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Validated
@Controller("/api/v1/")
@Requires(beans = LogRepositoryInterface.class)
public class LogController {
    @Inject
    private LogRepositoryInterface logRepository;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    protected QueueInterface<LogEntry> logQueue;

    /**
     * Search for logs
     *
     * @param query The lucene query
     * @param page The current page
     * @param size The current page size
     * @param sort The sort of current page
     * @return Paged log result
     */
    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "logs/search", produces = MediaType.TEXT_JSON)
    public PagedResults<LogEntry> find(
        @QueryValue(value = "q") String query,
        @QueryValue(value = "page", defaultValue = "1") int page,
        @QueryValue(value = "size", defaultValue = "10") int size,
        @Nullable @QueryValue(value = "minLevel") Level minLevel,
        @Nullable @QueryValue(value = "sort") List<String> sort
    ) {
        return PagedResults.of(
            logRepository.find(query, PageableUtils.from(page, size, sort), minLevel)
        );
    }

    /**
     * Get execution log
     *
     * @param executionId The execution identifier

     * @return Paged log result
     */
    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "logs/{executionId}", produces = MediaType.TEXT_JSON)
    public List<LogEntry> findByExecution(
        String executionId,
        @Nullable @QueryValue(value = "minLevel") Level minLevel,
        @Nullable @QueryValue(value = "taskRunId") String taskRunId,
        @Nullable @QueryValue(value = "taskId") String taskId
    ) {
        if (taskId != null) {
            return logRepository.findByExecutionIdAndTaskId(executionId, taskId, minLevel);
        } else if (taskRunId != null) {
            return logRepository.findByExecutionIdAndTaskRunId(executionId, taskRunId, minLevel);
        } else {
            return logRepository.findByExecutionId(executionId, minLevel);
        }
    }

    /**
     * Follow log for a specific execution
     *
     * @param executionId The execution id to follow
     * @return execution log sse event
     */
    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "logs/{executionId}/follow", produces = MediaType.TEXT_EVENT_STREAM)
    public Flowable<Event<LogEntry>> follow(String executionId, @Nullable @QueryValue(value = "minLevel") Level minLevel) {
        AtomicReference<Runnable> cancel = new AtomicReference<>();
        List<String> levels = LogEntry.findLevelsByMin(minLevel);

        return Flowable
            .<Event<LogEntry>>create(emitter -> {
                // fetch repository first
                logRepository.findByExecutionId(executionId, minLevel)
                    .stream()
                    .filter(logEntry -> levels.contains(logEntry.getLevel().name()))
                    .forEach(logEntry -> emitter.onNext(Event.of(logEntry).id("progress")));

                // consume in realtime
                Runnable receive = this.logQueue.receive(current -> {
                    if (current.getExecutionId() != null && current.getExecutionId().equals(executionId)) {
                        if (levels.contains(current.getLevel().name())) {
                            emitter.onNext(Event.of(current).id("progress"));
                        }
                    }
                });

                cancel.set(receive);
            }, BackpressureStrategy.BUFFER)
            .doOnCancel(() -> {
                if (cancel.get() != null) {
                    cancel.get().run();
                }
            })
            .doOnComplete(() -> {
                if (cancel.get() != null) {
                    cancel.get().run();
                }
            });
    }
}
