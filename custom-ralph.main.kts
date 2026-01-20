#!/usr/bin/env kotlin

import java.io.File

fun readOutput(vararg command: String): String =
    ProcessBuilder(command.toList())
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .start()
        .inputStream
        .bufferedReader()
        .readText()

enum class Agent(val execute: (task: String) -> Int) {
    JUNIE({ task ->
        ProcessBuilder(
            "junie",
            "--task",
            task,
            "--guidelines-filename",
            File("AGENTS.md").absoluteFile.toString(),
            "-t",
            "5000"
        ).inheritIO().start().waitFor()
    }),
    CLAUDE({ task ->
        ProcessBuilder(
            "claude",
            "--dangerously-skip-permissions",
            "--print",
            task,
        ).inheritIO().start().waitFor()
    }),
    GEMINI({ task ->
        ProcessBuilder(
            "gemini",
            "--prompt",
            task,
            "--yolo"
        ).inheritIO().start().waitFor()
    }),
    Codex({ task ->
        ProcessBuilder(
            "codex",
            "--sandbox",
            "workspace-write",
            task
        ).inheritIO().start().waitFor()
    })
}

fun git(vararg args: String) =
    ProcessBuilder("git", *args).inheritIO().start().waitFor()

// copy working dir to /agent/branch-name
// create branch-name branch
// start junie ralph in folder

(0..20).forEach { index ->
    val agent = Agent.valueOf(args[0])
    println(
        """
        #########################################################
        ########### ${agent.name} Ralph'ing iteration $index ####
        #########################################################
    """.trimIndent()
    )
    val prompt = """
        We're securing our microservice based system. specs/SERVICE_TO_SERVICE_AUTH_SPEC.md serves as a guideline.
        Take the single smallest issue, and start working on it. Complete the session according to instructions. 
        
        # Issues ready to be worked on
        ${readOutput("br", "ready")}
    """.trimIndent()
    agent.execute(prompt)
}
