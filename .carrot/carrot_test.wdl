version 1.0

task test_htsjdk_cram_fidelity {
    input {
        File input_file
        File output_file
        File reference_file
        String samtools_fmt
        String image_to_use
    }
    command {
        java -jar \
          ./build/libs/htsjdk-*-SNAPSHOT-local.jar \
           convert \
           ~{input_file} \
           ~{reference_file} \
           "~{samtools_fmt}"
    }
    runtime {
        docker: image_to_use
    }

    output {
        String result = stdout()
    }
}

workflow test_htsjdk_cram_fidelity_workflow {
    input {
        File input_file
        File output_file
        File reference_file
        String samtools_fmt
        String image_to_use
    }
    call test_htsjdk {
        input:
            input_file = input_file,
            output_file = output_file,
            reference_file = reference_file,
            samtools_fmt = samtools_fmt,
            image_to_use = image_to_use
    }
    output {
        String result = test_htsjdk.result
    }
}


