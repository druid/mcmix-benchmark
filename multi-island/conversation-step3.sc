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

	uint nrOfUsers = argument("nrOfUsers");
	uint mSize = argument("mSize");
	print(nrOfUsers);
	print(mSize);

	// a is a vector of tuples (wid, m)
	pd_shared3p uint64[[1]] a_vec = argument("a");
	print(size(a_vec));
	pd_shared3p uint64[[2]] a = reshape(a_vec, nrOfUsers, mSize+1);

	uint32 sectionType = newSectionType("conversation-step3");
	uint32 section = startSection(sectionType, nrOfUsers);

	// Sort by wid

	print("Oblivious shuffle");
	uint[[1]] p = iota(nrOfUsers); // vector of 1...n
	pd_shared3p xor_uint64[[1]] idx = p;

	pd_shared3p uint8[[1]] key = _shuffleKeyGen();
	a = shuffleRows(a, key);
	idx = shuffle(idx, key);

	print("Quicksort (t)");
	uint[[1]] perm = _unsafeSort(a[:,0], idx);

	print("Applying permutation");
	for (uint j = 0; j < 1+mSize; ++j)
		a[:,j] = _applyPermutation(a[:,j], perm);

	// Eliminate duplicates
	print("Eliminate duplicates");
	// Get LSB of message:
	pd_shared3p xor_uint64[[1]] xor_m = reshare(a[:,1]);
	pd_shared3p bool[[1]] bitvec = bit_extract(xor_m);
	pd_shared3p bool[[2]] bits = reshape(bitvec, nrOfUsers, 64);

	// Is wid equal to the next one?
	pd_shared3p bool[[1]] eq = a[:nrOfUsers-1,0] == a[1:,0];

	// If in a pair of equal wid-s the first message is lsb=0, then choose the next second message:
	a[:nrOfUsers-1,1] = choose(eq & !bits[:nrOfUsers-1,0], a[1:,1], a[:nrOfUsers-1,1]);

	// Mark all second wid-s in equal pairs zero:
	pd_shared3p uint64[[1]] zeros (nrOfUsers-1) = 0;
	a[1:,0] = choose(eq, zeros, a[1:,0]);

	// Sort again according to wid:
	print("Oblivious shuffle");
	idx = p;

	key = _shuffleKeyGen();
	a = shuffleRows(a, key);
	idx = shuffle(idx, key);

	print("Quicksort (t)");
	perm = _unsafeSort(a[:,0], idx);

	print("Applying permutation");
	for (uint j = 0; j < 1+mSize; ++j)
		a[:,j] = _applyPermutation(a[:,j], perm);

	// Idea: eliminateDuplicates may be done with special radix sort that only sorts by LSB
	// It might not work

	publish("a", a[:,1:]);
	endSection(section);
	print("Step 3: Done");
}
