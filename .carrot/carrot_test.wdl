version 1.0

task test_htsjdk_cram_fidelity {
    input {
        File input_file
        File reference_file
        String samtools_fmt
        String image_to_use
    }
    command {
        htsjdk_jar=$(ls -A /build/libs | head -n 1)

        java -jar \
           "/build/libs/$htsjdk_jar" \
           convert \
           ~{input_file} \
           output.cram \
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
        File reference_file
        String samtools_fmt
        String image_to_use
    }
    call test_htsjdk_cram_fidelity {
        input:
            input_file = input_file,
            reference_file = reference_file,
            samtools_fmt = samtools_fmt,
            image_to_use = image_to_use
    }
    output {
        String result = test_htsjdk_cram_fidelity.result
    }
}


