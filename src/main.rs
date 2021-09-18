use std::env;

#[derive(Debug)]
enum Command {
    Install {
        candidate: Option<String>,
        version: Option<String>,
        local_path: Option<String>,
    },
    Initialise,
    Uninstall,
    List,
    Use,
    Default,
    Home,
    Env,
    Current,
    Config,
    Upgrade,
    Version,
    Broadcast,
    Help,
    Offline,
    SelfUpdate,
    Update,
    Flush,
}
fn main() {
    let command_arg = env::args().nth(1);
    let candidate_arg = env::args().nth(2);
    let version_arg = env::args().nth(3);
    let local_path_arg = env::args().nth(4);

    let command = match command_arg.unwrap_or_else(|| String::from("")).as_ref() {
        "init" => Some(Command::Initialise),
        "i" | "install" => Some(Command::Install {
            candidate: candidate_arg,
            version: version_arg,
            local_path: local_path_arg,
        }),
        "rm" | "uninstall" => Some(Command::Uninstall),
        "ls" | "list" => Some(Command::List),
        "u" | "use" => Some(Command::Use),
        "config" => Some(Command::Config),
        "d" | "default" => Some(Command::Default),
        "h" | "home" => Some(Command::Home),
        "e" | "env" => Some(Command::Env),
        "c" | "current" => Some(Command::Current),
        "ug" | "upgrade" => Some(Command::Upgrade),
        "v" | "version" => Some(Command::Version),
        "b" | "broadcast" => Some(Command::Broadcast),
        "help" => Some(Command::Help),
        "offline" => Some(Command::Offline),
        "selfupdate" => Some(Command::SelfUpdate),
        "update" => Some(Command::Update),
        "flush" => Some(Command::Flush),
        _ => None,
    };

    match command {
        Some(cmd) => println!("{:?}", cmd),
        None => display_usage(),
    }

    println!("version {}", env!("CARGO_PKG_VERSION"));
    println!("{:?}", sys_info::os_type());
}

fn display_usage() {
    let msg = "\n
Usage: devkit <command> [candidate] [version]
       devkit offline <enable|disable>

   commands:
       install   or i   <candidate> [version] [local-path]
       uninstall or rm   <candidate> <version>
       list      or ls   [candidate]
       use       or u    <candidate> <version>
       config
       default   or d    <candidate> [version]
       home      or h    <candidate> <version>
       env       or e    [init|install|clear]
       current   or c    [candidate]
       upgrade   or ug   [candidate]
       version   or v
       broadcast or b
       help
       offline           [enable|disable]
       selfupdate        [force]
       update
       flush             [archives|tmp|broadcast|version]

   candidate  :  the SDK to install: groovy, scala, grails, gradle, kotlin, etc.
                 use list command for comprehensive list of candidates
                 eg: $ devkit list
   version    :  where optional, defaults to latest stable if not provided
                 eg: $ devkit install groovy
   local-path :  optional path to an existing local installation
                 eg: $ devkit install groovy 2.4.13-local /opt/groovy-2.4.13
";

    println!("{}", msg);
}
