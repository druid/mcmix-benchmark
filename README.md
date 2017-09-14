# MCMix benchmarking code

This repository contains SecreC benchmark code used for the paper:

Nikolaos Alexopoulos, Aggelos Kiayias, Riivo Talviste, and Thomas Zacharias. **MCMix: Anonymous messaging via secure multiparty computation.** In _26th USENIX Security Symposium (USENIX Security 17)_, pages 1217--1234, Vancouver, BC, 2017. USENIX Association. ([PDF][1], [eprint][2])

## Requirements

* [Sharemind MPC](https://sharemind.cyber.ee/) Application or Academic Server
* SecreC 2 compiler and standard library
* Sharemind MPC pre-2016.12 proxy controller (jswcp) support for running multi-island benchmarks
	* Sharemind MPC 2017.03 and newer with Web Application Gateway support require some small changes in `proxy.js`
	* Also, NodeJS is required to run the proxy application

## License

This work is licensed under the Apache 2.0 License.

[1]: https://www.usenix.org/conference/usenixsecurity17/technical-sessions/presentation/alexopoulos
[2]: https://eprint.iacr.org/2017/778