package com.aubb.server.modules.judge.infrastructure.gojudge;

import java.util.List;
import java.util.Map;

public interface GoJudgeClient {

    List<RunResult> run(RunRequest request);

    record RunRequest(List<Command> cmd) {}

    record Command(
            List<String> args,
            List<FileDescriptor> files,
            long cpuLimit,
            long clockLimit,
            long memoryLimit,
            int procLimit,
            Map<String, CopyInFile> copyIn) {}

    sealed interface FileDescriptor permits MemoryFileDescriptor, CollectorFileDescriptor {}

    record MemoryFileDescriptor(String content) implements FileDescriptor {}

    record CollectorFileDescriptor(String name, long max) implements FileDescriptor {}

    record CopyInFile(String content) {}

    record RunResult(
            String status, Integer exitStatus, Long time, Long memory, Long runTime, Map<String, String> files) {}
}
