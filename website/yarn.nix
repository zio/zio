let pkgs = import <nixpkgs> {};

in pkgs.mkShell rec {
  name = "webdev";
  
  buildInputs = with pkgs; [
    nodejs yarn
  ];
}    
