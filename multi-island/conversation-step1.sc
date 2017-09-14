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
	uint mSize = argument("mSize");  // Message size (times 64 bits)
	print(nrOfUsers);
	print(mSize);

	uint32 sectionType = newSectionType("conversation-step1");
	uint32 section = startSection(sectionType, nrOfUsers);

	// a: triples (t, wid, m)
	pd_shared3p uint64[[2]] a (nrOfUsers, 2 + mSize);

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

	publish("a", a);
	endSection(section);
	print("Step 1: Done");
}
