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
	print("Hello");

	uint32 sectionType = newSectionType("conversation");

	pd_shared3p uint64[[1]] testSizes = {100, 500, 1000, 5000, 10000, 50000, 100000};
	pd_shared3p uint64[[1]] testsp = cycle(testSizes, size(testSizes) * 5); // Repeat each test 5 times
	testsp = shuffle(testsp);
	uint64[[1]] tests = declassify(testsp);

	for (uint i = 0; i < size(tests); ++i) {

		// Sleep for ~8 seconds
		// This makes it easier to split the bandwidth log
		// file for individual tests.
		uint sleep;
		for (uint k = 0; k < 1000000; ++k) {
			for (uint j = 0; j < 200; ++j) {
				sleep = k+j;
			}
		}

		uint nrOfUsers = tests[i];
		print(nrOfUsers);

		// a: triples (t, wid, m)
		uint mSize = 1; // Message size (times 64 bits)
		pd_shared3p uint64[[2]] a (nrOfUsers, 2 + mSize);

		uint32 section = startSection(sectionType, nrOfUsers);

		// Initially, the matrix is sorted by wid:
		for (uint i = 0; i < nrOfUsers; ++i)
			a[i, 1] = i;

		print("Oblivious shuffle");
		uint[[1]] p = iota(nrOfUsers); // vector of 1...n
		pd_shared3p xor_uint64[[1]] idx = p;

		pd_shared3p uint8[[1]] key = _shuffleKeyGen();
		a = shuffleRows(a, key);
		idx = shuffle(idx, key);

		print("Quicksort (t)");
		uint[[1]] perm = _unsafeSort(a[:,0], idx);

		print("Applying permutation");
		for (uint j = 0; j < 2+mSize; ++j)
			a[:,j] = _applyPermutation(a[:,j], perm);

		// Is a.1 equal to the next one?
		// size(eq) == nrOfUsers-1

		print("Calculating equality");
		pd_shared3p bool[[1]] eq = a[:nrOfUsers-1,0] == a[1:,0];

		// Oblivious message exchange
		// Instead of messages, we exchange wid-s, 
		// as they have constant size.
		// Mark exchange with LSB

		// Equal with previous is more important
		// 1. b = (message equal with next) ? i+1 : i;
		print("Oblivious choice (b)");
		//pd_shared3p uint64[[1]] b_m = choose(eq, a[1:,2] + 1, a[:nrOfUsers-1,2]);
		pd_shared3p uint64[[1]] b_wid = choose(eq, a[1:,1], a[:nrOfUsers-1,1]);
		print("- cat(b)");
		//b_m = cat(b_m, {a[nrOfUsers-1,2]});
		b_wid = cat(b_wid, {a[nrOfUsers-1,1]});

		// 2. c = (message equal with previous) ? i-1 : b;
		print("Oblivious choice (c)");
		//pd_shared3p uint64[[1]] c_m = choose(eq, a[:nrOfUsers-1,2] + 1, b_m[1:]);
		pd_shared3p uint64[[1]] c_wid = choose(eq, a[:nrOfUsers-1,1], b_wid[1:]);
		print("- cat(c)");
		//c_m = cat({b_m[0]}, c_m);
		c_wid = cat({b_wid[0]}, c_wid);

		// End result must be sorted according to wid again:
		print("Oblivious shuffle");
		idx = p;

		key = _shuffleKeyGen();
		c_wid = shuffle(c_wid, key);
		pd_shared3p uint64[[2]] c_m = shuffleRows(a[:,2:], key);
		idx = shuffle(idx, key);

		print("Quicksort");
		perm = _unsafeSort(c_wid, idx);

		print("Applying permutation");
		for (uint j = 0; j < mSize; ++j)
			c_m[:,j] = _applyPermutation(c_m[:,j], perm);

		print(size(c_m));
		//publish("a", c_m);
		print("Conversation: done");

		endSection(section);
		flushProfileLog();

	}

	// TODO: Idea: eliminateDuplicates may be done with special radix sort that only sorts by LSB
}
