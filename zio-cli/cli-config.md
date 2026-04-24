# Cli Configuration

> It is possible to tweak the behavior of our `CliApp` specifying a custom `CliConfig` in the parameter `cliConfig` of method `CliApp.make`. By default, `CliApp.make` uses `CliConfig.default`. `CliConfig` specifies how a `CliApp`
determines the valid commands from the `command: Command[Model]` parameter.

It is possible to tweak the behavior of our `CliApp` specifying a custom `CliConfig` in the parameter `cliConfig` of method `CliApp.make`. By default, `CliApp.make` uses `CliConfig.default`. `CliConfig` specifies how a `CliApp`
determines the valid commands from the `command: Command[Model]` parameter.

## Parameters

You can construct directly a `CliConfig`:
```scala
final case class CliConfig(
  caseSensitive: Boolean,
  autoCorrectLimit: Int,
  finalCheckBuiltIn: Boolean = true,
  showAllNames: Boolean = true,
  showTypes: Boolean = true
)
```

`CliConfig` allows to control case sensitivity, autocorrection behaviour, command processing and
help appearance.
### Case sensitivity
It is controlled by field `caseSensitive`. If it is `true`, then a `CliApp` will determine as distinct uppercase and lowercase versions of a letter in a command. On the other hand, `caseSensitive = false` implies that the `CliApp` will treat uppercase and lowercase letters as the same. In the Git example, we would have:

- `caseSensitive = true`
```
git clone  
GIT cloNE
```
The first will be detected as the `git clone` command while the second will trigger an error.

- `caseSensitive = false`
```
git clone   // Detected by CLI as "git clone" command
GIT cloNE   // Detected by CLI as "git clone" command
```
Both commands will be detected as the `git clone` command.

### Autocorrection
It is controlled by the field `autoCorrectLimit`. It is the number of mistakes that can be corrected when parsing the name of an option introduced by a user. If the CLI detects that the user has written an incorrect name for the option and the number of mistakes is less, it will suggest the correct option. If `autoCorrectLimit=2` and the user inputs 
`git status --bran nameOfBranch` instead of `git status --branch nameOfBranch`, the output produced by the CLI app will be 
```
The flag "--bran" is not recognized. Did you mean --branch?
```
On the other hand, if the user writes `git status --bra nameOfBranch`, the CLI app will not be able to detect the 3 mistakes and will produce
```
Expected to find --branch option.
```

## Command processing
`finalCheckBuiltIn` controls whether after an invalid command is entered by the user, there is a final check searching for a flag like `--help`, `-h` or `--wizard`. In this case, the corresponding Help or Wizard Mode of the parent command is triggered. Note that this can only trigger the parent command predefined option because the entered command is not valid, so it is an "emergency" check.

## Help appearance
`showAllNames` controls whether all the names of an option are shown in the usage synopsis of a command:
```
command (-o, --option text)      # showAllNames = true
```
`showTypes` controls whether the type of the option is shown in the usage synopsis of a command.
```
command (-o, --option text)      # showAllNames = true,  showTypes = true
command --option text            # showAllNames = false, showTypes = true
command (-o, --option )          # showAllNames = true,  showTypes = false
command --option                 # showAllNames = false, showTypes = false
```

## Default configuration

The default configuration is given by
```scala
object CliConfig {
  val default: CliConfig = CliConfig(caseSensitive = false, autoCorrectLimit = 2)
}
```
This means that a `CliApp` that does not specify any `CliConfig` and uses `CliConfig.default` will:
- ignore if the letters of a command are written in uppercase or lowercase,
- correct automatically up to two mistakes when writing the name of an option in a command of `CliApp`,
- trigger Help or Wizard Mode if the corresponding option is found after an invalid command was entered and
- show full usage synopsis of commands.
