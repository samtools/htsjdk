version 1.0

task compare {
    input {
        String result
        String image_to_use
    }
    command {
        echo "Checking the result from previous test:"
        echo "~{result}"
    }
    runtime {
        docker: image_to_use
    }
    output {
        File comparison_result = stdout()
    }
}

workflow eval_workflow {
    input {
        String result
        String image_to_use
    }
    call compare {
        input:
            result = result,
            image_to_use = image_to_use
    }
    output {
        File comparison_result = compare.comparison_result
    }
}