# Common server

Common server project makes CRUD operations available by calling functions from dao-lib project on one end, and on the other it recieves requests mainly made by common-client project. It also checks if actions are allowed for logged in user.

## Getting Started

This project is ment to be used only with common-middle (where are defined URLs and available functionalities), common-client where are implemented three entities User, Role and Language, server-lib (which passes clients requests to routing function) and dao-lib (which executes CRUD operations over mongo database).

### Installing

You can use this project as dependencie in clojure projects by listing it in project.clj

```
[org.clojars.vladimirmarkovic86/common-server "0.3.0"]
```

## Authors

* **Vladimir Markovic** - [VladimirMarkovic86](https://github.com/VladimirMarkovic86)

## License

This project is licensed under the Eclipse Public License 1.0 - see the [LICENSE](LICENSE) file for details

