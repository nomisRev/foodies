#!/usr/bin/env kotlin

import java.io.File

fun junie(task: String): Int =
    ProcessBuilder(
        "junie",
        "--task",
        task,
        "--project",
        File("../").absoluteFile.toString()
    ).inheritIO().start().waitFor()

fun git(vararg args: String) =
    ProcessBuilder("git", *args).inheritIO().start().waitFor()

// copy working dir to /agent/branch-name
// create branch-name branch
// start junie ralph in folder

(0..5).forEach { index ->
    println(
        """
        #########################################################
        ########### Junie Ralph'ing iteration $index ############
        #########################################################
    """.trimIndent()
    )
    junie(File("../PROMPT.MD").readText())
}
