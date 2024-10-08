version 1.0

task test_htsjdk {
    input {
        File input_file
        String image_to_use
    }
    command {
        echo "pretending to run test on ~{input_file}"
    }
    runtime {
        docker: image_to_use
    }

    output {
        String result = stdout()
    }
}

workflow test_workflow {
    input {
        File input_file
        String image_to_use
    }
    call test_htsjdk {
        input:
            input_file = input_file,
            image_to_use = image_to_use
    }
    output {
        String result = test_htsjdk.result
    }
}


