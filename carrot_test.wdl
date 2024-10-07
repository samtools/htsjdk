version 1.0

task runTest {
    input {
        String name
        String dockerHash
    }


    command {
        echo 'hello ${name}!'
        echo 'from: ${dockerHash}'
    }

    output {
        File response = stdout()
    }

    runtime {
        docker: 'ubuntu:latest'
    }
}

workflow htsjdkTest {
    call runTest
}