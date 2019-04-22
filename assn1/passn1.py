#------------------------------
# Course: CSCI 4130 001
# Authors: Sellars, Walter
#          Lamm, William
#          Carson, Adam
# Date: 01-10-2019
# File: passn1.py
# Description: The script will read a directory of containing files
#   of text. It will then perform some analysis on the files and create
#   files containing the following statistics: 
#
#      - Character Frequencies
#      - Unigram Frequencies
#      - Bigram Frequencies
#      - Trigram Frequencies
#------------------------------
import tkinter, os, re, string, csv

from collections import Counter
from tkinter.filedialog import askdirectory

# ask for a directory and get all the files
directory = ''
olddir    = os.getcwd()
files     = []

charFrequency = [0] * 26
uni  = Counter({})  # unigram dictionary
bi   = Counter({})  # bigram dictionary
tri  = Counter({})  # trigram dictionary

# toString will transfer the charFrequency array to a csv output
def arrToString(data):
    string = ''
    for i in range(0, len(data)):
        string += (chr(i+97) + "," + str(data[i])) + "\n"
    return string

# go through each letter in each word and increase the charFrequency count
def charCount(data):
    for w in data:
        for c in w:
            if c.isalpha():
                pos = ord(c.lower()) - 97
                if pos >= 0 and pos <= 25: charFrequency[pos] += 1

# calculate the ngram frequencies
def ngrams(input, n): 
    output = {}
    for i in range(len(input) - n + 1):
        g = ' '.join(input[i:i+n])
        output.setdefault(g,0)
        output[g] += 1
    return Counter(output)

# wite a list of tuples to a csv
def tupleToCsv(name, data):
    with open(name, 'w') as f:
        writer = csv.writer(f)
        writer.writerows(data)

# initilize the tkinter root window and hide it
root = tkinter.Tk()
root.wm_withdraw()

# prompt for a directory to read and get all the files in it
directory = askdirectory()
files = sorted(os.listdir(directory))

# destory tkinter window
root.destroy()


os.chdir(directory)

for file in files:
    if file.endswith('.txt'):
        print("working on " + file + "...")
        with open(file, 'r', encoding='ISO-8859-1') as input:

            # get the input and lowercase all words
            text    = input.read().split()
            tokens  = [word.lower() for word in text]

            # strip punctuation and blank elements
            re_punc  = re.compile('[%s]' % re.escape(string.punctuation))
            stripped = [re_punc.sub('',w) for w in tokens if w.isalpha()]
            words    = [word for word in stripped if word != '']

            uni += ngrams(words,1)   # get unigrams
            bi  += ngrams(words,2)   # get bigrams
            tri += ngrams(words,3)   # get trigrams

            charCount(text)   # get character frequencies on unedited data

os.chdir(olddir)

# transfer the dictionaries to alphabetically sorted lists
alpha_uni = sorted(uni.items(), key=lambda x: x[0])
alpha_bi  = sorted(bi.items(),  key=lambda x: x[0])
alpha_tri = sorted(tri.items(), key=lambda x: x[0])

# transfer the dictionaries to numerically sorted lists (descending order) 
sorted_uni = sorted(uni.items(), key=lambda x: x[1], reverse=True)
sorted_bi  = sorted(bi.items(),  key=lambda x: x[1], reverse=True)
sorted_tri = sorted(tri.items(),  key=lambda x: x[1], reverse=True)

# alphabetically sorted ngram files
tupleToCsv('uni_alpha.csv', alpha_uni)   
tupleToCsv('bii_alpha.csv', alpha_bi)
tupleToCsv('tii_alpha.csv', alpha_tri)

# numerically sorted ngram files (descending order on frequency counts)
tupleToCsv('uni.csv', sorted_uni)
tupleToCsv('bi_dat.csv', sorted_bi)
tupleToCsv('tri_dat.csv', sorted_tri)