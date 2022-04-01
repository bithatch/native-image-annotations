package uk.co.bithatch.nativeimage.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface TypeReflect {
	boolean fields() default false;
	boolean classes() default false;
	boolean methods() default false;
	boolean constructors() default false;
}
