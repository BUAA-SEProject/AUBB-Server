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

    record FileDescriptor(String content, String name, Long max) {}

    record CopyInFile(String content) {}

    record RunResult(
            String status, Integer exitStatus, Long time, Long memory, Long runTime, Map<String, String> files) {}
}
