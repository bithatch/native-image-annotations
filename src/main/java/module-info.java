module uk.co.bithatch.nativeimage.annotations {
	requires com.google.gson;
	requires transitive java.compiler;
    requires static com.google.auto.service;
	exports uk.co.bithatch.nativeimage.annotations;
    opens uk.co.bithatch.nativeimage.annotations;  
	provides javax.annotation.processing.Processor with uk.co.bithatch.nativeimage.annotations.NativeImageProcessor;
}