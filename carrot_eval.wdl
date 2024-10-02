version 1.0

task runEval {
    input {
        String name
        String dockerHash
    }


    command {
        echo "EVAL EVAL EVAL!"
        echo
    }

    output {
        File response = stdout()
    }

    runtime {
        docker: 'ubuntu:latest'
    }
}

workflow htsjdkEval {
    call runEval
    output {
        String outputMessage = runEval.response
    }
}