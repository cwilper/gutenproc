# gutenproc

Processes the Project Gutenberg DVD from 2010.

## Installation

**Prerequisites:**

* JDK 8
* Maven 3
* A locally-mounted [Project Gutenberg 2010 DVD](https://www.gutenberg.org/wiki/Gutenberg:The_CD_and_DVD_Project).

**Cloning and Building:**

    git clone https://github.com/cwilper/gutenproc.git
    cd gutenproc
    mvn package

**Execution:**

The easy way to start using gutenproc is just to add ``/path/to/gutenproc/src/main/bash`` to your ``PATH``, which will put make the ``gutenproc`` command available to you.

You can also just run ``java -jar target/gutenproc.jar`` and not worry about putting it in your path.

## Usage

Basic usage of ``gutenproc`` follows the pattern:

    gutenproc processor -d /path/to/DVD [processor-options..]

_Tip: Instead of specifying ``-d /path/to/DVD`` with every invocation of ``gutenproc``, you may also define the ``PGDVD_PATH`` environment variable._

**Processors:**

* **list:** Prints book metadata
* **unique:** Prints unique metadata values or a summary of unique value counts
* **dspace:** Creates a directory of items that can be ingested into a [DSpace](http://dspace.org/) repository.

Enter any of these, followed by ``-h`` to see processor-specific options. Some options, such as filtering by metadata values, are common to all processors.

## Examples

List all English books.

    gutenproc list --match-language English

List all books with plaintext containing the phrase "unmatched force", with between 300 and 400 lines of text, showing the matching lines and total number of lines in the book.

    gutenproc list --match-text "unmatched force" --min-lines 300 \
                   --max-lines 400 --print-matches --show-computed

List the top ten authors among all books whose titles begin with "The", ordered by number of matching books.

    gutenproc unique --field Author --show-counts --show-top 10 \
                     --match-title 's/The.*/'

Create a directory in DSpace Simple Archive Format with one item for each English book whose title contains the word "space". Each item should have the original content file from the DVD at a minimum, and if plaintext is available, a generated ``.pdf`` with Project Gutenberg header and footer text removed should be included as an additional bitstream.

	gutenproc dspace --output-dir ingest-me --match-title space \
                      --match-language English --generate-stripped-pdf
