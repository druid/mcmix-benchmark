/**
 * Copyright 2017 Nikolaos Alexopoulos (Telecooperation Lab, TU Darmstadt, alexopoulos@tk.tu-darmstadt.de)
 * Copyright 2017 Cybernetica AS
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
     * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import stdlib;
import oblivious;
import shared3p;
import shared3p_sort;
import shared3p_random;
import profiling;
import analytics;

domain pd_shared3p shared3p;

void main() {
	uint32 sectionType = newSectionType("dialing");

	pd_shared3p uint64[[1]] testSizes = {100, 500, 1000, 5000, 10000, 50000, 100000, 500000};
	pd_shared3p uint64[[1]] testsp = cycle(testSizes, size(testSizes) * 5); // Repeat each test 5 times
	testsp = shuffle(testsp);
	uint64[[1]] tests = declassify(testsp);

	for (uint i = 0; i < size(tests); ++i) {

		// Sleep for ~4 seconds
		// This makes it easier to split the bandwidth log
		// file for individual tests.
		uint sleep;
		for (uint k = 0; k < 1000000; ++k) {
			for (uint j = 0; j < 100; ++j) {
				sleep = k+j;
			}
		}

		uint nrOfUsers = tests[i];
		print(nrOfUsers);

		// a is a sequence of tuples (pk, pk, wid)
		// pk and wid are both 64 bits
		pd_shared3p uint64[[2]] a (nrOfUsers, 3);

		// A vector of public keys:
		// This is also assumed to be in the right order
		pd_shared3p uint64[[1]] pubkeys (nrOfUsers);
		pubkeys = iota(nrOfUsers);

		uint32 section = startSection(sectionType, nrOfUsers);

		// Initially, the matrix is sorted by wid:
		a[:,2] = iota(nrOfUsers);

		// Discard messages with invalid public keys:
		print("Discarding invalid messages");
		pd_shared3p bool[[1]] isInvalid = (a[:,0] != pubkeys) & (a[:,1] != pubkeys);
		pd_shared3p uint64[[1]] zeros (nrOfUsers) = 0;

		a[:,0] = choose(isInvalid, zeros, a[:,0]);
		a[:,1] = choose(isInvalid, zeros, a[:,1]);

		// Sort according to second coordinate:
		print("Oblivious shuffle");
		uint[[1]] p = iota(nrOfUsers); // vector of 1...n
		pd_shared3p xor_uint64[[1]] idx = p;

		pd_shared3p uint8[[1]] key = _shuffleKeyGen();
		a = shuffleRows(a, key);
		idx = shuffle(idx, key);

		print("Quicksort (a[1])");
		uint[[1]] perm = _unsafeSort(a[:,1], idx);

		print("Applying permutation");
		for (uint j = 0; j < 3; ++j)
			a[:,j] = _applyPermutation(a[:,j], perm);

		// Identify communicating users
		print("Identifying dialcheckers");
		pd_shared3p uint64[[1]] dialcheck (nrOfUsers) = 12345; // Some constant
		pd_shared3p bool[[1]] isDialchecker = a[:,0] == dialcheck;

		print("Calculating equality");
		pd_shared3p bool[[1]] eq = a[:nrOfUsers-1,1] == a[1:,1];

		// Accrording to the algorithm, 
		// equal with previous is more important

		// 1. (dialchecker and equal with next) ? i+1 : 0;
		pd_shared3p uint64[[1]] c = choose(isDialchecker[:nrOfUsers-1] & eq, a[1:,0], zeros[:nrOfUsers-1]);
		c = cat(c, {0}); // Last one cannot be equal to next one

		// 2. (dialchecker and equal to previous) ? i-1 : c;
		pd_shared3p uint64[[1]] b = choose(isDialchecker[1:] & eq, a[:nrOfUsers-1,0], c[1:]);
		b = cat({c[0]}, b); // First item cannot be equal to previous

		// We do not put 2-column b together here,
		// we just sort by wid (in a[2] at the moment)
		// and then permute elements in b accordingly
		print("Oblivious shuffle");
		idx = p; // reuse

		key = _shuffleKeyGen();
		pd_shared3p uint64[[1]] wid_column = shuffle(a[:,2], key);
		b = shuffle(b, key);
		idx = shuffle(idx, key);

		print("Quicksort (wid)");
		perm = _unsafeSort(wid_column, idx);

		print("Applying permutation to b");
		b = _applyPermutation(b, perm);

		print(size(b));
		print("Dialing: done");

		endSection(section);
		flushProfileLog();
	}
}
