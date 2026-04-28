package org.l2x6.jrebuild.core.build.service.samples;

public class ClassPerson1 {
    private final String firstName;
    private String lastName;

    public ClassPerson1(String firstName, String lastName) {
        super();
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @Override
    public String toString() {
        return firstName + " " + lastName;
    }
}
