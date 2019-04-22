# Authors: Carson, Adam 
#          Lamm, William
#          Sellars, Walter
# Date: 3 - 7 - 2019
# Course: CSCI 4130 
# Description: passn2.py will create a positional index of a corpus and 
#   have the ability to handle phrase and proximity queries

import os, re                          
import _pickle as pickle                     # loading and dumping of index
from nltk.stem import PorterStemmer as ps    # stemmer
from tqdm import tqdm                        # used for progress bars

# read a corpus at a directory given by the user, data stored is the raw text.
def readCorpus():
	global dr, dataFile, data

	dr    = input("Corpus Location: ")
	files = sorted(os.listdir(dr))

	# read the content raw content of each file
	for i in tqdm(range(len(files)), desc='Reading:  ', ncols=80, unit='files'):
		with open(os.path.join(dr, files[i]), 'r', encoding='ISO-8859-1') as f:
			data.append((files[i], f.read().lower()))		

# preprocess will remove all special chars
def process(data):
	text = re.sub(r'[@#$%^&?<>*()\t=\-;\\\]\[\"\']', '', data.lower())
	text = re.sub(r'[\n\r!.,:]', ' ', text).split(' ')
	text = list(filter(None, text))
	text = [ps().stem(w) for w in text]
	return text

# indexer will create a positional index for the corpus
# datastructure for an index: {word: {doc1: [positions], doc2: [positions]}, word2: ...}
def genIndex():
	global index, data

	for i in tqdm(range(len(data)), desc='Indexing', ncols=80, unit='files'):
		item = data[i]
		text = process(item[1])
		for i, w in enumerate(text): # add positions to the dictionary, offsettings for 0
			if w not in index: index[w] = {item[0]: [i + 1]}
			elif item[0] not in index[w]: index[w].update({item[0]: [i + 1]})
			else: index[w][item[0]].append(i + 1)		

# readIndex will take 'index.dat' and create a positional index from it
def readIndex():
	global index, dataFile
	with open(os.path.join(os.getcwd(), dataFile), 'rb') as f: index = pickle.load(f)

# writeIndex will take a positional index and write it to the file 'index.dat'
def writeIndex():
	global index, dataFile
	with open(os.path.join(os.getcwd(), dataFile), 'wb') as f: pickle.dump(index, f)

# debugging purposes only
def writeIndexPlain():
	global index, dataFile
	with open(os.path.join(os.getcwd(), "index_plain.dat"), 'w') as f: f.write(str(index))

# query will run a query on the positional index and display the results
def query(dat):
	global index

	if len(dat) == 1: # single word query

		if dat[0] in index:
			for key, value in index[dat[0]].items():
				if value: print("'%s' has %d match(es)" % (key, len(value)))

		else: print("No results were found for the query.")         # one or more words not in the index, display a string
	elif len(dat) == 2 or len(dat) == 3: # phrase query or a distance query

		if dat[0] in index and dat[len(dat) - 1] in index: # ensure that both words are in the index

			maxLen  = 1 if len(dat) == 2 else int(re.sub(r'/', '', dat[1])) # allowed distance between the two words
			word1   = [(k,v) for k, v in index[dat[0]].items()]             # get the documents and positions for the first word
			word2   = [(k,v) for k, v in index[dat[len(dat) - 1]].items()]  # get the documents and positions for the second word
			matches = ""                                                    # intialize the matches string

			# iterate through each word list
			for doc1, pos1 in word1:
				for doc2, pos2 in word2:
					if doc1 != doc2: continue # only compare the same documents

					# find all the matches, and append them to the matches string
					match = sorted(list(set([j for j in pos1 for k in pos2 if (k > j and j + maxLen >= k)])))
					match = sorted(list(set(match + [k for k in pos2 for j in pos1 if (j > k and k + maxLen) >= j])))
					
					if match: matches += "\nDocument'%s': \n%s\n" % (doc1, ' '.join(map(str, match)))

			if len(matches): print(matches)                         # display the results if any
			else: print("No results were found for the query.")     # no results, display a string

		else: print("No results were found for the query.")         # one or more words not in the index, display a string
	
# getHelp will display the options for queryMain
def getHelp(): 
	print("\nHelp Menu:\n\tQuery Format: word /<int> word\n\tExit: ~quit\n\n")

# the main function for querying data
def queryMain():
	global index

	while True: # Continually ask for input, each input given is stemmed and 
		q = process(input("\nEnter a query, or type ’~help’ for info: "))
		if q[0] == '~quit': break               # user has requested an exit
		if q[0] == '~help': getHelp(); continue # display the available options
		query(q)                                # run the requested query

# General Kenobi, our lord and savior
def helloThere():
	global dataFile

	# check to see if we need to create the index
	if os.path.isfile(dataFile):
		print(os.path.join(os.getcwd(), dataFile))
		print("Loading index..."); readIndex(); print("Finished loading...")
		queryMain()
	else:
		readCorpus(); genIndex()
		writeIndex(); queryMain()

if __name__ == '__main__':
	data     = []   # store the file information and data
	index    = {}   # positional index of a corpus
	dr       = ""   # the directory where the corpus is located
	dataFile = "index.dat"

	helloThere()