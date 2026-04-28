package org.l2x6.jrebuild.core.build.service.samples;

public final class ClassPerson2 {
    String firstName;
    private String lastName;

    public ClassPerson2(String firstName, String lastName) {
        super();
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @Override
    public String toString() {
        return "firstName=" + firstName + ", lastName=" + lastName + "]";
    }
}
