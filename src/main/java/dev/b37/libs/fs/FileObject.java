package dev.b37.libs.fs;

public class FileObject {
    private FileObjectType type;
    private String name;

    public FileObject() {
    }

    public FileObject(FileObjectType type, String name) {
        this.type = type;
        this.name = name;
    }

    public FileObjectType getType() {
        return type;
    }

    public void setType(FileObjectType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
