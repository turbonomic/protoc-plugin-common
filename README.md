# protoc-plugin-common
protoc-plugin-common is a module containing a framework for protobuf compiler plugin creation.

The meat of the framework is an abstract class - ```ProtocPluginCodeGenerator``` - which can be extended
by specific plugins to generate code for enums, messages, and services.
If you want to create a protobuf compiler plugin you need to do:
   1) Extend ```ProtocPluginCodeGenerator``` (e.g. ```MyPluginCodeGenerator```) and fill in the methods.
   2) Create a ```Main``` class and call: ```new MyPluginCodeGenerator().generate()```

The relevant files are:
   - ```Registry```, which keeps track of processed messages, enums, and services and allows accessing their descriptors by name.
   - ```*Descriptor``` classes, which wrap around the *DescriptorProto classes to provide some additional information and utility methods.
   - ```FileDescriptorProcessingContext```, which keeps track of the traversal through each .proto (we need this mainly to find comments).
   - ```ProtocPluginCodeGenerator```, which has some methods to actually go through the whole generation process.
